package com.ibox.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.model.ProxyInfo;
import com.ibox.model.req.ibox.BatchPurchaseConsignmentOrdersReq;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.MarketCollectionListRes;
import com.ibox.model.res.ibox.MarketCollectionRes;
import com.ibox.service.IboxAccountService;
import com.ibox.service.IboxProxyService;
import com.ibox.utils.FeishuMessageUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 分类 101 新品监听并自动批量下单任务。
 * <p>
 * 规则：
 * 1. 循环拉取分类 101 商品；
 * 2. 首次获取仅建立基线，不触发下单；
 * 3. 后续如发现新品，则所有账号并发发起批量下单；
 * 4. 每次下单固定数量 5、金额 2000；
 * 5. 商品拉取走代理，下单仍使用账号直连链路。
 * </p>
 */
@Slf4j
@Component
public class Category101NewCollectionPurchaseTask {

    /**
     * 目标分类 ID。
     */
    private static final int TARGET_SEGMENT_ID = 102;

    /**
     * 单次拉取页码。
     */
    private static final int PAGE_NO = 1;

    /**
     * 单次拉取数量。
     */
    private static final int PAGE_SIZE = 20;

    /**
     * 批量下单固定数量。
     */
    private static final int PURCHASE_COUNT = 5;

    /**
     * 批量下单固定金额上限。
     */
    private static final int MAX_SINGLE_PRICE = 2000;

    /**
     * 新品轮询间隔。
     */
    private static final long LOOP_INTERVAL_MILLIS = 150L;

    private final IboxClient iboxClient;
    private final IboxAccountService iboxAccountService;
    private final IboxProxyService iboxProxyService;
    private final FeishuMessageUtil feishuMessageUtil;

    /**
     * 新品下单并发线程池。
     */
    private final ExecutorService purchaseExecutor = Executors.newFixedThreadPool(16);

    /**
     * 新品监听后台线程池。
     */
    private final ExecutorService monitorExecutor = Executors.newSingleThreadExecutor();

    /**
     * 已经见过的商品 ID 集合。
     */
    private final Set<Long> seenCollectionIds = ConcurrentHashMap.newKeySet();

    /**
     * 可用账号列表，只在启动时加载一次。
     */
    private volatile List<IboxAccountEntity> cachedAccounts = List.of();

    /**
     * 是否已经完成首次基线建立。
     */
    private volatile boolean baselineInitialized = false;

    public Category101NewCollectionPurchaseTask(IboxClient iboxClient,
                                                IboxAccountService iboxAccountService,
                                                IboxProxyService iboxProxyService,
                                                FeishuMessageUtil feishuMessageUtil) {
        this.iboxClient = iboxClient;
        this.iboxAccountService = iboxAccountService;
        this.iboxProxyService = iboxProxyService;
        this.feishuMessageUtil = feishuMessageUtil;
    }

    /**
     * 启动时加载账号，并开启 while(true) 后台监听线程。
     */
    @PostConstruct
    public void startMonitorLoop() {
        this.cachedAccounts = loadAvailableAccountsOnce();
        log.info("【分类101新品监听】启动完成，已加载账号数量:{}", cachedAccounts.size());
        monitorExecutor.submit(() -> {
            while (true) {
                try {
                    monitorAndPurchaseNewCollections();
                } catch (Exception e) {
                    log.error("【分类101新品监听】监听循环异常", e);
                }
                sleepQuietly(LOOP_INTERVAL_MILLIS);
            }
        });
    }

    /**
     * 循环拉取分类 101 商品，并在发现新品时触发所有账号并发批量下单。
     */
    private void monitorAndPurchaseNewCollections() {
        List<MarketCollectionRes> currentCollections = fetchCategoryCollectionsWithProxy();
        if (currentCollections.isEmpty()) {
            return;
        }
        log.info("【分类101新品监听】获取商品成功-----{}",currentCollections.size());
        if (!baselineInitialized) {
            for (MarketCollectionRes collection : currentCollections) {
                if (collection != null && collection.getId() != null) {
                    seenCollectionIds.add(collection.getId());
                }
            }
            baselineInitialized = true;
            log.info("【分类101新品监听】首次建立基线完成，collectionCount:{}", seenCollectionIds.size());
            return;
        }

        List<MarketCollectionRes> newCollections = new ArrayList<>();
        for (MarketCollectionRes collection : currentCollections) {
            if (collection == null || collection.getId() == null) {
                continue;
            }
            if (seenCollectionIds.add(collection.getId())) {
                newCollections.add(collection);
            }
        }

        if (newCollections.isEmpty()) {
            return;
        }

        if (cachedAccounts.isEmpty()) {
            log.warn("【分类101新品监听】发现新品但无可用账号，newCollectionCount:{}", newCollections.size());
            return;
        }

        for (MarketCollectionRes collection : newCollections) {
            if (collection.getFloorPrice().compareTo(new BigDecimal(10))>=0){
                log.info("【分类101新品监听】发现新品，准备全账号并发批量下单，collectionId:{}, name:{}, accountCount:{}",
                        collection.getId(), collection.getName(), cachedAccounts.size());
                for (IboxAccountEntity account : cachedAccounts) {
                    purchaseExecutor.submit(() -> tryBatchPurchase(account, collection));
                }
            }
        }
    }

    /**
     * 通过代理拉取分类 101 商品列表。
     */
    private List<MarketCollectionRes> fetchCategoryCollectionsWithProxy() {
        try {
            String url = String.format(UrlConstant.MARKET_COLLECTION_LIST_URL, PAGE_NO, TARGET_SEGMENT_ID, PAGE_SIZE);
            ProxyInfo proxyInfo = iboxProxyService.getNextConfigProxy();
            IboxApiResponse<MarketCollectionListRes> response = iboxClient.get("", url, null, new TypeReference<>() {
            }, proxyInfo);
            if (response == null || response.getData() == null || response.getData().getList() == null) {
                log.warn("【分类101新品监听】获取商品列表失败，response:{}", response);
                return List.of();
            }
            return response.getData().getList();
        } catch (Exception e) {
            log.error("【分类101新品监听】拉取分类商品异常", e);
            return List.of();
        }
    }

    /**
     * 启动时一次性加载全部可用账号。
     * 只保留 bearerToken 不为空的账号。
     */
    private List<IboxAccountEntity> loadAvailableAccountsOnce() {
        List<IboxAccountEntity> accounts = iboxAccountService.lambdaQuery()
            .orderByAsc(IboxAccountEntity::getId)
            .list();
        if (accounts == null || accounts.isEmpty()) {
            return List.of();
        }
        return accounts.stream()
            .filter(account -> account != null && StringUtils.hasText(account.getBearerToken()))
            .toList();
    }

    /**
     * 使用单个账号对新品发起一次批量下单。
     */
    private void tryBatchPurchase(IboxAccountEntity account, MarketCollectionRes collection) {
        try {
            BatchPurchaseConsignmentOrdersReq req = new BatchPurchaseConsignmentOrdersReq();
            req.setDigitalCollectionGroupId(collection.getId());
            req.setMaxCount(PURCHASE_COUNT);
            req.setMaxSinglePrice(MAX_SINGLE_PRICE);
            req.setPaymentPlatformCode(account.getDefaultPaymentPlatformCode() == null ? 30 : account.getDefaultPaymentPlatformCode());
            req.setLevel(-1);

            IboxApiResponse<Map<String, Object>> response = iboxClient.post(
                account.getBearerToken(),
                String.format(UrlConstant.BATCH_PURCHASE_CONSIGNMENT_ORDER_URL, account.getIboxUid()),
                req,
                new TypeReference<>() {
                }
            );
            log.info("【分类101新品监听】批量下单完成，accountId:{}, collectionId:{}, name:{}, response:{}",
                account.getId(), collection.getId(), collection.getName(), response);
            if (isPurchaseSuccess(response)) {
                String orderId = String.valueOf(response.getData().get("orderId"));
                String message = "iBox分类101新品批量下单成功，账号ID：" + account.getId()
                    + "，账号名称：" + safeText(account.getAccountName())
                    + "，商品ID：" + collection.getId()
                    + "，商品名称：" + safeText(collection.getName())
                    + "，订单ID：" + orderId;
                feishuMessageUtil.sendText(message, "category101-buy-success:" + account.getId() + ":" + collection.getId() + ":" + orderId, 3);
            }
        } catch (Exception e) {
            log.error("【分类101新品监听】批量下单异常，accountId:{}, collectionId:{}, name:{}",
                account == null ? null : account.getId(),
                collection == null ? null : collection.getId(),
                collection == null ? null : collection.getName(),
                e);
        }
    }

    /**
     * 判断批量下单是否成功。
     */
    private boolean isPurchaseSuccess(IboxApiResponse<Map<String, Object>> response) {
        return response != null
            && response.getCode() != null
            && response.getCode() == 0
            && response.getData() != null
            && response.getData().get("orderId") != null;
    }

    /**
     * 文本空值兜底。
     */
    private String safeText(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    /**
     * 静默休眠，避免监听循环被中断异常刷日志。
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.max(millis, 0L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
