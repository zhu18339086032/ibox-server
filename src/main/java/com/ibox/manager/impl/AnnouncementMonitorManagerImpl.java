package com.ibox.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.common.AnnouncementStateCache;
import com.ibox.common.IboxAccountCache;
import com.ibox.common.MarketCollectionCache;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.IboxMonitorTaskConfigEntity;
import com.ibox.entity.MarketCollectionEntity;
import com.ibox.manager.AnnouncementMonitorManager;
import com.ibox.model.ProxyInfo;
import com.ibox.model.req.ibox.BatchPurchaseConsignmentOrdersReq;
import com.ibox.model.res.ibox.*;
import com.ibox.model.res.web.MatchedMarketCollectionRes;
import com.ibox.service.IboxAccountService;
import com.ibox.service.IboxProxyService;
import com.ibox.service.MarketCollectionService;
import com.ibox.utils.AnnouncementContentParser;
import com.ibox.utils.FeishuMessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 同时监控公告与推荐位事件，并在命中时触发购买尝试。
 */
@Slf4j
@Service
public class AnnouncementMonitorManagerImpl implements AnnouncementMonitorManager {

    /**
     * 候选藏品允许下单的最小地板价。
     */
    private static final BigDecimal MIN_FLOOR_PRICE = new BigDecimal("20");

    /**
     * 首页推荐位的固定 areaType。
     */
    private static final int RECOMMEND_AREA_TYPE = 1;

    /**
     * 从推荐位链接中提取商品组 ID。
     */
    private static final Pattern MARKET_LINK_ID_PATTERN = Pattern.compile("#/markets/(\\d+)(?:[?/#]|$)");

    /**
     * iBox API 客户端。
     */
    @Autowired
    private IboxClient iboxClient;

    /**
     * 账号服务。
     */
    @Autowired
    private IboxAccountService iboxAccountService;

    /**
     * 市场藏品服务。
     */
    @Autowired
    private MarketCollectionService marketCollectionService;

    /**
     * 市场藏品缓存。
     */
    @Autowired
    private MarketCollectionCache marketCollectionCache;

    /**
     * 账号缓存。
     */
    @Autowired
    private IboxAccountCache iboxAccountCache;

    /**
     * 公告与推荐位状态缓存。
     */
    @Autowired
    private AnnouncementStateCache announcementStateCache;

    /**
     * 公告内容解析器。
     */
    @Autowired
    private AnnouncementContentParser announcementContentParser;

    /**
     * 公告轮询代理服务。
     */
    @Autowired
    private IboxProxyService iboxProxyService;

    /**
     * 飞书通知工具。
     */
    @Autowired
    private FeishuMessageUtil feishuMessageUtil;

    /**
     * 轮询最新公告，并在匹配成功时尝试下单。
     */
    @Override
    public void monitorLatestAnnouncement(IboxMonitorTaskConfigEntity config) {
        long startAt = System.nanoTime();
        IboxAccountEntity account = getAccount(config);
        if (account.getBearerToken() == null || account.getBearerToken().isBlank()) {
            throw new IllegalStateException("账号列表中不存在可用 token");
        }
        Integer paymentPlatformCode = resolvePaymentPlatformCode(account, config);

        ProxyInfo announcementListProxy = iboxProxyService.getNextConfigProxy();
        long listStartAt = System.nanoTime();
        IboxApiResponse<IboxPageDataRes<AnnouncementRes>> listResponse = iboxClient.get("", String.format(UrlConstant.ANNOUNCEMENT_LIST_URL, -1, 1, 20), null, new TypeReference<>() {
        }, announcementListProxy);
        long listCostMs = elapsedMs(listStartAt);

        if (listResponse == null || listResponse.getData() == null || listResponse.getData().getList() == null || listResponse.getData().getList().isEmpty()) {
            return;
        }
        AnnouncementRes latest = listResponse.getData().getList().getFirst();

        Long cachedLatestId = announcementStateCache.getLatestAnnouncementId();
        if (cachedLatestId == null) {
            announcementStateCache.setLatestAnnouncementId(latest.getId());
            return;
        }
        log.info("【公告列表】公告UUID:{},标题:{},分类:{}, listCostMs:{}", latest.getUuid(), latest.getTitle(), latest.getCategory().getName(), listCostMs);
        if (latest.getId().equals(cachedLatestId)) {
            return;
        }
        announcementStateCache.setLatestAnnouncementId(latest.getId());

        if (shouldSkipDetailFetch(latest.getCategory().getName(), latest.getTitle())) {
            log.info("【公告监控】合成公告标题命中调整类关键词，跳过详情请求: {}", latest.getTitle());
            return;
        }

        long detailStartAt = System.nanoTime();
        IboxApiResponse<AnnouncementRes> detailResponse = iboxClient.get(account.getBearerToken(), String.format(UrlConstant.ANNOUNCEMENT_DETAIL_URL, latest.getUuid()), null, AnnouncementRes.class);
        long detailCostMs = elapsedMs(detailStartAt);
        if (detailResponse == null || detailResponse.getData() == null) {
            log.warn("【公告监控】公告详情为空, uuid:{}, listCostMs:{}, detailCostMs:{}", latest.getUuid(), listCostMs, detailCostMs);
            return;
        }

        AnnouncementRes detail = detailResponse.getData();
        long parseStartAt = System.nanoTime();
        List<String> collectionNames = announcementContentParser.parseCollectionNames(latest.getTitle(), latest.getCategory().getName(), detail.getContent());
        long parseCostMs = elapsedMs(parseStartAt);
        if (collectionNames == null || collectionNames.isEmpty()) {
            log.warn("【公告监控】公告未解析出可用藏品, 公告UUID:{}, listCostMs:{}, detailCostMs:{}, parseCostMs:{}", latest.getUuid(), listCostMs, detailCostMs, parseCostMs);
            return;
        }

        ensureMarketCacheLoaded();
        int purchaseCount = resolvePurchaseCount(config);
        BigDecimal priceLimit = config != null ? config.getMaxPrice() : null;

        long matchStartAt = System.nanoTime();
        List<MatchedMarketCollectionRes> candidates = resolveMatchedCandidates(collectionNames, priceLimit);
        long matchCostMs = elapsedMs(matchStartAt);
        if (candidates.isEmpty()) {
            log.warn("【公告监控】未匹配到可下单藏品, uuid:{}, listCostMs:{}, detailCostMs:{}, parseCostMs:{}, matchCostMs:{}", latest.getUuid(), listCostMs, detailCostMs, parseCostMs, matchCostMs);
            return;
        }

        for (MatchedMarketCollectionRes matched : candidates) {
            log.info("【批量下单】开始尝试购买藏品:{}, ID:{}, 地板价:{}", matched.getName(), matched.getId(), matched.getFloorPrice());
            BatchPurchaseConsignmentOrdersReq req = buildBatchPurchaseRequest(matched.getId(), purchaseCount, resolveMaxSinglePrice(matched.getFloorPrice(), priceLimit), paymentPlatformCode);

            long purchaseStartAt = System.nanoTime();
            IboxApiResponse<Map<String, Object>> purchaseResponse = submitBatchPurchase(account, req);
            long purchaseCostMs = elapsedMs(purchaseStartAt);
            if (isPurchaseSuccess(purchaseResponse)) {
                String orderUuid = extractOrderUuid(purchaseResponse);
                log.info("【公告监控耗时】uuid:{}, listCostMs:{}, detailCostMs:{}, parseCostMs:{}, matchCostMs:{}, purchaseCostMs:{}, totalCostMs:{}, candidate:{}", latest.getUuid(), listCostMs, detailCostMs, parseCostMs, matchCostMs, purchaseCostMs, elapsedMs(startAt), matched.getName());
                notifyOrderSuccess(account, latest, matched, orderUuid);
                return;
            }
            log.info("【批量下单】下单失败:{}, purchaseCostMs:{}, candidate:{}", JSONObject.toJSONString(purchaseResponse), purchaseCostMs, matched.getName());
        }

        log.warn("【公告监控耗时】所有候选下单失败, uuid:{}, listCostMs:{}, detailCostMs:{}, parseCostMs:{}, matchCostMs:{}, totalCostMs:{}, candidates:{}", latest.getUuid(), listCostMs, detailCostMs, parseCostMs, matchCostMs, elapsedMs(startAt), candidates.size());
    }


    @Override
    public void monitorRecommendArea(IboxMonitorTaskConfigEntity config) {
        long startAt = System.nanoTime();
        IboxAccountEntity account = getAccount(config);
        if (account.getBearerToken() == null || account.getBearerToken().isBlank()) {
            throw new IllegalStateException("账号列表中不存在可用 token");
        }
        if (config == null || config.getId() == null) {
            throw new IllegalStateException("推荐位监控任务配置缺少任务ID");
        }

        long listStartAt = System.nanoTime();
        IboxApiResponse<HomeActivityAreaListRes> response = iboxClient.get("", String.format(UrlConstant.HOME_ACTIVITY_AREAS_URL, RECOMMEND_AREA_TYPE), null, HomeActivityAreaListRes.class);
        long listCostMs = elapsedMs(listStartAt);
        if (response == null || response.getData() == null || response.getData().getList() == null || response.getData().getList().isEmpty()) {
            return;
        }

        List<HomeActivityAreaItemRes> list = response.getData().getList();
        Set<Long> seenIds = announcementStateCache.getOrCreateRecommendAreaSeenIds(config.getId());
        if (seenIds.isEmpty()) {
            for (HomeActivityAreaItemRes item : list) {
                if (item != null && item.getId() != null) {
                    seenIds.add(item.getId());
                }
            }
            log.info("【推荐位监控】首次启动建立基线, taskId:{}, areaType:{}, baselineCount:{}, listCostMs:{}", config.getId(), RECOMMEND_AREA_TYPE, seenIds.size(), listCostMs);
            return;
        }

        List<HomeActivityAreaItemRes> newItems = new ArrayList<>();
        for (HomeActivityAreaItemRes item : list) {
            if (item == null || item.getId() == null || seenIds.contains(item.getId())) {
                continue;
            }
            newItems.add(item);
        }
        if (newItems.isEmpty()) {
            return;
        }

        int purchaseCount = resolvePurchaseCount(config);
        Integer paymentPlatformCode = resolvePaymentPlatformCode(account, config);
        BigDecimal priceLimit = config.getMaxPrice();
        ensureMarketCacheLoaded();

        for (HomeActivityAreaItemRes item : newItems) {
            if (item.getId() != null) {
                seenIds.add(item.getId());
            }
            String link = item.getLink();
            Long collectionGroupId = extractMarketCollectionGroupId(link);
            if (collectionGroupId == null) {
                log.info("【推荐位监控】跳过非商品链接, taskId:{}, areaId:{}, link:{}, listCostMs:{}", config.getId(), item.getId(), link, listCostMs);
                continue;
            }

            MarketCollectionEntity entity = marketCollectionCache.getById(collectionGroupId);
            BigDecimal maxSinglePrice = resolveRecommendAreaMaxSinglePrice(entity, priceLimit);
            String candidateName = entity == null || entity.getName() == null || entity.getName().isBlank() ? String.valueOf(collectionGroupId) : entity.getName();
            log.info("【推荐位监控】检测到新增推荐位, taskId:{}, areaId:{}, collectionId:{}, collectionName:{}, link:{}, listCostMs:{}", config.getId(), item.getId(), collectionGroupId, candidateName, link, listCostMs);

            BatchPurchaseConsignmentOrdersReq req = buildBatchPurchaseRequest(collectionGroupId, purchaseCount, maxSinglePrice.intValue(), paymentPlatformCode);
            long purchaseStartAt = System.nanoTime();
            IboxApiResponse<Map<String, Object>> purchaseResponse = submitBatchPurchase(account, req);
            long purchaseCostMs = elapsedMs(purchaseStartAt);
            if (isPurchaseSuccess(purchaseResponse)) {
                String orderUuid = extractOrderUuid(purchaseResponse);
                log.info("【推荐位监控耗时】taskId:{}, areaId:{}, collectionId:{}, purchaseCostMs:{}, totalCostMs:{}, result:success", config.getId(), item.getId(), collectionGroupId, purchaseCostMs, elapsedMs(startAt));
                notifyRecommendAreaPurchaseSuccess(account, item, entity, orderUuid);
                continue;
            }
            log.info("【推荐位监控】购买失败, taskId:{}, areaId:{}, collectionId:{}, purchaseCostMs:{}, response:{}", config.getId(), item.getId(), collectionGroupId, purchaseCostMs, JSONObject.toJSONString(purchaseResponse));
        }
    }

    @Override
    public void clearRecommendAreaState(Long taskId) {
        announcementStateCache.clearRecommendAreaSeenIds(taskId);
    }

    /**
     * 跳过明显不可能产生可购目标的公告调整类通知。
     */
    private boolean shouldSkipDetailFetch(String categoryName, String title) {
        if (categoryName == null || title == null) {
            return false;
        }
        if (!categoryName.contains("合成公告")) {
            return false;
        }
        return title.contains("时间调整") || title.contains("娲诲姩时间调整") || title.contains("公告调整") || title.contains("修正");
    }

    /**
     * 返回从给定起始时间到现在的毫秒耗时。
     */
    private long elapsedMs(long startAt) {
        return (System.nanoTime() - startAt) / 1_000_000;
    }

    /**
     * 根据解析出的藏品名称解析可下单候选。
     */
    private List<MatchedMarketCollectionRes> resolveMatchedCandidates(List<String> collectionNames, BigDecimal priceLimit) {
        LinkedHashSet<Long> seenIds = new LinkedHashSet<>();
        List<MatchedMarketCollectionRes> candidates = new ArrayList<>();
        for (String name : collectionNames) {
            MarketCollectionEntity entity = marketCollectionCache.getByName(name);
            if (entity == null || entity.getId() == null || !seenIds.add(entity.getId())) {
                continue;
            }
            BigDecimal floorPrice = entity.getFloorPrice();
            if (!isPriceAcceptable(floorPrice, priceLimit)) {
                log.warn("【公告监控】藏品价格不满足条件，跳过: {}, floorPrice:{}, priceLimit:{}", name, floorPrice, priceLimit);
                continue;
            }
            MatchedMarketCollectionRes matched = new MatchedMarketCollectionRes();
            matched.setId(entity.getId());
            matched.setName(entity.getName());
            matched.setFloorPrice(floorPrice);
            candidates.add(matched);
            log.info("【公告监控】匹配到藏品:{}, ID:{}, 地板价:{}", entity.getName(), entity.getId(), entity.getFloorPrice());
        }
        candidates.sort(Comparator.comparing(MatchedMarketCollectionRes::getFloorPrice));
        log.info("【公告监控】候选尝试顺序:{}", candidates.stream().map(item -> item.getName() + "(" + formatPrice(item.getFloorPrice()) + ")").toList());
        return candidates;
    }

    /**
     * 检查地板价是否满足过滤条件。
     */
    private boolean isPriceAcceptable(BigDecimal floorPrice, BigDecimal priceLimit) {
        if (floorPrice == null) {
            return false;
        }
        if (floorPrice.compareTo(MIN_FLOOR_PRICE) < 0) {
            return false;
        }
        return priceLimit == null || floorPrice.compareTo(priceLimit) <= 0;
    }

    /**
     * 计算批量购买请求中的单价上限。
     */
    private int resolveMaxSinglePrice(BigDecimal floorPrice, BigDecimal priceLimit) {
        if (priceLimit != null) {
            return priceLimit.intValue();
        }
        if (floorPrice == null) {
            return 0;
        }
        return floorPrice.multiply(new BigDecimal("1.5")).intValue();
    }

    private BigDecimal resolveRecommendAreaMaxSinglePrice(MarketCollectionEntity entity, BigDecimal priceLimit) {
        if (priceLimit != null) {
            return priceLimit;
        }
        if (entity != null && entity.getFloorPrice() != null && entity.getFloorPrice().compareTo(BigDecimal.ZERO) > 0) {
            return entity.getFloorPrice().multiply(new BigDecimal("1.5"));
        }
        return BigDecimal.ZERO;
    }

    private Integer resolvePaymentPlatformCode(IboxAccountEntity account, IboxMonitorTaskConfigEntity config) {
        return config != null && config.getPaymentPlatformCode() != null ? config.getPaymentPlatformCode() : (account.getDefaultPaymentPlatformCode() == null ? 30 : account.getDefaultPaymentPlatformCode());
    }

    private int resolvePurchaseCount(IboxMonitorTaskConfigEntity config) {
        return config != null && config.getMaxCount() != null ? config.getMaxCount() : 4;
    }

    private BatchPurchaseConsignmentOrdersReq buildBatchPurchaseRequest(Long collectionGroupId, int purchaseCount, int maxSinglePrice, Integer paymentPlatformCode) {
        BatchPurchaseConsignmentOrdersReq req = new BatchPurchaseConsignmentOrdersReq();
        req.setDigitalCollectionGroupId(collectionGroupId);
        req.setMaxCount(purchaseCount);
        req.setMaxSinglePrice(maxSinglePrice);
        req.setPaymentPlatformCode(paymentPlatformCode);
        req.setLevel(-1);
        return req;
    }

    private IboxApiResponse<Map<String, Object>> submitBatchPurchase(IboxAccountEntity account, BatchPurchaseConsignmentOrdersReq req) {
        return iboxClient.post(account.getBearerToken(), String.format(UrlConstant.BATCH_PURCHASE_CONSIGNMENT_ORDER_URL, account.getIboxUid()), req, new TypeReference<>() {
        });
    }

    private boolean isPurchaseSuccess(IboxApiResponse<Map<String, Object>> purchaseResponse) {
        return purchaseResponse != null && purchaseResponse.getCode() != null && purchaseResponse.getCode() == 0;
    }

    private String extractOrderUuid(IboxApiResponse<Map<String, Object>> purchaseResponse) {
        if (purchaseResponse == null || purchaseResponse.getData() == null || purchaseResponse.getData().get("orderId") == null) {
            return null;
        }
        return String.valueOf(purchaseResponse.getData().get("orderId"));
    }

    private Long extractMarketCollectionGroupId(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        Matcher matcher = MARKET_LINK_ID_PATTERN.matcher(link);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 发送下单成功通知，并在可能时附带订单详情。
     */
    private void notifyOrderSuccess(IboxAccountEntity account, AnnouncementRes latest, MatchedMarketCollectionRes matched, String orderUuid) {
        try {
            if (orderUuid == null || orderUuid.isBlank()) {
                feishuMessageUtil.sendText("iBox批量下单成功，来源：公告监控，公告标题：" + latest.getTitle() + "，藏品名称：" + matched.getName());
                return;
            }

            IboxApiResponse<OrderDetailRes> detailResponse = iboxClient.get(account.getBearerToken(), String.format(UrlConstant.ORDER_DETAIL_URL, orderUuid), null, OrderDetailRes.class);
            if (detailResponse == null || detailResponse.getCode() == null || detailResponse.getCode() != 0 || detailResponse.getData() == null) {
                feishuMessageUtil.sendText("iBox批量下单成功，来源：公告监控，公告标题：" + latest.getTitle() + "，藏品名称：" + matched.getName() + "，订单ID：" + orderUuid);
                return;
            }

            feishuMessageUtil.sendCard(buildOrderSuccessCard(latest, matched, detailResponse.getData()));
        } catch (Exception e) {
            log.warn("【飞书通知】发送下单成功通知失败", e);
        }
    }

    private void notifyRecommendAreaPurchaseSuccess(IboxAccountEntity account, HomeActivityAreaItemRes item, MarketCollectionEntity entity, String orderUuid) {
        String collectionName = entity == null || entity.getName() == null || entity.getName().isBlank() ? String.valueOf(item.getId()) : entity.getName();
        try {
            if (orderUuid == null || orderUuid.isBlank()) {
                feishuMessageUtil.sendText("iBox批量下单成功，来源：推荐位监控，推荐位ID：" + item.getId() + "，藏品名称：" + collectionName);
                return;
            }
            IboxApiResponse<OrderDetailRes> detailResponse = iboxClient.get(account.getBearerToken(), String.format(UrlConstant.ORDER_DETAIL_URL, orderUuid), null, OrderDetailRes.class);
            if (detailResponse == null || detailResponse.getCode() == null || detailResponse.getCode() != 0 || detailResponse.getData() == null) {
                feishuMessageUtil.sendText("iBox批量下单成功，来源：推荐位监控，推荐位ID：" + item.getId() + "，藏品名称：" + collectionName + "，订单ID：" + orderUuid);
                return;
            }
            OrderDetailRes detail = detailResponse.getData();
            feishuMessageUtil.sendText("iBox批量下单成功，来源：推荐位监控，推荐位ID：" + item.getId() + "，藏品名称：" + safeText(collectionName) + "，订单数量：" + (detail.getQuantity() == null ? "-" : detail.getQuantity()) + "，订单总价：" + formatPrice(detail.getPrice()) + "，订单ID：" + safeText(detail.getOrderUuid()));
        } catch (Exception e) {
            log.warn("【飞书通知】发送推荐位下单成功通知失败", e);
        }
    }

    /**
     * 构建下单成功的飞书卡片。
     */
    private Map<String, Object> buildOrderSuccessCard(AnnouncementRes latest, MatchedMarketCollectionRes matched, OrderDetailRes detail) {
        String collectionName = matched.getName();
        if (detail.getProductPreview() != null && detail.getProductPreview().getName() != null && !detail.getProductPreview().getName().isBlank()) {
            collectionName = detail.getProductPreview().getName();
        }

        StringBuilder content = new StringBuilder();
        content.append("**来源：** 公告监控").append("\n**公告标题：** ").append(safeText(latest.getTitle())).append("\n**藏品名称：** ").append(safeText(collectionName)).append("\n**订单数量：** ").append(detail.getQuantity() == null ? "-" : detail.getQuantity()).append("\n**订单总价：** ").append(formatPrice(detail.getPrice())).append("\n**下单时间：** ").append(safeText(detail.getCreatedAt())).append("\n**订单ID：** ").append(safeText(detail.getOrderUuid()));

        if (detail.getProducts() != null && !detail.getProducts().isEmpty()) {
            for (OrderDetailRes.ProductItem product : detail.getProducts()) {
                content.append("\n价格：").append(formatPrice(product.getPrice()));
            }
        }

        Map<String, Object> header = new HashMap<>();
        header.put("title", Map.of("tag", "plain_text", "content", "iBox 下单成功"));
        header.put("template", "green");

        Map<String, Object> element = new HashMap<>();
        element.put("tag", "markdown");
        element.put("content", content.toString());

        Map<String, Object> card = new HashMap<>();
        card.put("config", Map.of("wide_screen_mode", true, "enable_forward", true));
        card.put("header", header);
        card.put("elements", List.of(element));
        return card;
    }

    /**
     * 格式化价格并去掉末尾无意义的零。
     */
    private String formatPrice(BigDecimal price) {
        return price == null ? "-" : price.stripTrailingZeros().toPlainString();
    }

    /**
     * 为空白文本提供可打印的兜底值。
     */
    private String safeText(Object text) {
        if (text == null) {
            return "-";
        }
        String value = String.valueOf(text);
        return value.isBlank() ? "-" : value;
    }

    /**
     * 按需加载市场藏品缓存。
     */
    private void ensureMarketCacheLoaded() {
        if (!marketCollectionCache.isEmpty()) {
            return;
        }
        List<MarketCollectionEntity> list = marketCollectionService.listMarketCollection();
        marketCollectionCache.refresh(list);
    }

    /**
     * 从缓存中获取默认账号。
     */
    private IboxAccountEntity getDefaultAccount() {
        IboxAccountEntity cached = iboxAccountCache.getDefaultAccount();
        if (cached != null) {
            return cached;
        }
        throw new IllegalStateException("默认账号缓存不存在，请检查启动预热任务是否执行成功");
    }

    /**
     * 解析任务配置中的账号，找不到时回退到默认账号。
     */
    private IboxAccountEntity getAccount(IboxMonitorTaskConfigEntity config) {
        if (config != null && config.getAccountId() != null) {
            IboxAccountEntity account = iboxAccountCache.getById(config.getAccountId());
            if (account != null) {
                return account;
            }
            account = iboxAccountService.getById(config.getAccountId());
            if (account == null) {
                throw new IllegalStateException("监控任务绑定账号不存在");
            }
            iboxAccountCache.put(account);
            return account;
        }
        return getDefaultAccount();
    }
}
