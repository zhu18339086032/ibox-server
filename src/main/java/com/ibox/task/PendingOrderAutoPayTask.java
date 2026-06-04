package com.ibox.task;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.constants.BuyConstant;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.IboxPageDataRes;
import com.ibox.model.res.ibox.OrderDetailFullRes;
import com.ibox.model.res.ibox.OrderDetailRes;
import com.ibox.model.res.ibox.OrderPaymentInfoRes;
import com.ibox.model.res.ibox.OrderRes;
import com.ibox.service.IboxAccountService;
import com.ibox.utils.FeishuMessageUtil;
import com.ibox.utils.HfPayBalancePayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 待支付订单自动付款任务。
 * 每 5 秒扫描一次全部账号待支付订单，使用图片地址和购买任务中的图片映射进行匹配，
 * 再使用价格做二次确认，确保仅支付命中的购买任务订单。
 */
@Slf4j
@Component
public class PendingOrderAutoPayTask {

    /**
     * 单次最多拉取的待支付订单数。
     * 当前先扫描首页，避免单轮请求过重。
     */
    private static final int PAGE_SIZE = 20;

    /**
     * 距离订单过期小于该秒数时，才允许自动付款。
     */
    private static final long AUTO_PAY_EXPIRE_THRESHOLD_SECONDS = 20L;

    /**
     * 订单详情时间字段格式。
     */
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IboxAccountService iboxAccountService;
    private final IboxClient iboxClient;
    private final FeishuMessageUtil feishuMessageUtil;

    /**
     * 总开关，便于在不改代码的情况下临时停用自动付款扫描。
     */
    @Value("${ibox.auto-pay.enabled:true}")
    private boolean autoPayEnabled;

    /**
     * 汇付余额支付密码。
     * 当前实现为统一配置，后续如果有账号维度密码，可以再下沉到账号配置中。
     */
    @Value("${ibox.auto-pay.password:}")
    private String autoPayPassword;

    public PendingOrderAutoPayTask(IboxAccountService iboxAccountService, IboxClient iboxClient, FeishuMessageUtil feishuMessageUtil) {
        this.iboxAccountService = iboxAccountService;
        this.iboxClient = iboxClient;
        this.feishuMessageUtil = feishuMessageUtil;
    }

    /**
     * 定时扫描全部账号的待支付订单。
     * 命中购买任务映射且价格校验通过时，进入自动付款入口。
     */
    @Scheduled(fixedDelay = 20000L, initialDelay = 5000L)
    public void scanPendingOrders() {
        if (!autoPayEnabled) {
            return;
        }
        if (autoPayPassword == null || autoPayPassword.isBlank()) {
            log.warn("【待支付自动付款】未配置支付密码，跳过本轮扫描");
            return;
        }

        if (BuyConstant.buyMap.isEmpty() || BuyConstant.picMap.isEmpty()) {
            return;
        }
        log.info("【待支付自动付款】命中购买任务映射，开始扫描，buyMap:{}, picMapSize:{}", BuyConstant.buyMap, BuyConstant.picMap.size());


        List<IboxAccountEntity> accounts = iboxAccountService.lambdaQuery().orderByAsc(IboxAccountEntity::getId).list();
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        // 所有账号独立扫描，单个账号异常不能影响其他账号继续处理。
        for (IboxAccountEntity account : accounts) {
            try {
                handleAccountPendingOrders(account);
            } catch (Exception e) {
                log.error("【待支付自动付款】处理账号异常，accountId:{}, accountName:{}", account == null ? null : account.getId(), account == null ? null : account.getAccountName(), e);
            }
        }
    }

    /**
     * 处理单个账号的待支付订单列表。
     * 当前实现只取首个待支付订单做匹配和后续处理。
     *
     * @param account iBox账号
     */
    private void handleAccountPendingOrders(IboxAccountEntity account) {
        // 没有 token 的账号无法查询订单，直接跳过。
        if (account == null || account.getBearerToken() == null || account.getBearerToken().isBlank()) {
            return;
        }

        List<OrderRes> orders = fetchPendingOrders(account);
        if (orders.isEmpty()) {
            return;
        }
        OrderRes firstOrderRes = orders.getFirst();
        IboxApiResponse<OrderDetailRes> detailResponse = iboxClient.get(account.getBearerToken(), String.format(UrlConstant.ORDER_DETAIL_URL, firstOrderRes.getOrderUuid()), null, OrderDetailRes.class);
        if (detailResponse == null || detailResponse.getCode() == null || detailResponse.getCode() != 0 || detailResponse.getData() == null) {
            return;
        }

        OrderDetailRes detail = detailResponse.getData();
        String dedupKey = "pending-order-card:" + account.getId() + ":" + safeOrderUuid(firstOrderRes);
        feishuMessageUtil.sendCard(buildPendingOrderCard(account, firstOrderRes, detail, orders.size()), dedupKey, 3);
        log.info("【待支付自动付款】获取首个待支付订单，accountId:{}, accountName:{}, orderUuid:{}, collectionName:{}, pendingCount:{}",
                account.getId(),
                safeText(account.getAccountName()),
                safeOrderUuid(firstOrderRes),
                resolveOrderName(firstOrderRes, detail),
                orders.size());
        processOrder(account, firstOrderRes);
    }

    /**
     * 构建“账号存在待支付订单”的飞书卡片。
     * 当前卡片用于提醒人工关注，因此把首单信息和当前待支付数量一起带上。
     *
     * @param account      iBox账号
     * @param order        首个待支付订单
     * @param detail       订单详情
     * @param pendingCount 当前账号待支付总数
     * @return 飞书交互卡片
     */
    private Map<String, Object> buildPendingOrderCard(IboxAccountEntity account, OrderRes order, OrderDetailRes detail, int pendingCount) {
        StringBuilder content = new StringBuilder();
        content.append("**来源：** 待支付订单巡检")
                .append("\n**账号ID：** ").append(account == null || account.getId() == null ? "-" : account.getId())
                .append("\n**账号名称：** ").append(account == null ? "-" : safeText(account.getAccountName()))
                .append("\n**待支付数量：** ").append(Math.max(pendingCount, 0))
                .append("\n**首单藏品：** ").append(resolveOrderName(order, detail))
                .append("\n**订单总价：** ").append(resolveOrderPrice(order, detail))
                .append("\n**订单数量：** ").append(resolveOrderQuantity(detail))
                .append("\n**下单时间：** ").append(detail == null ? "-" : safeText(detail.getCreatedAt()))
                .append("\n**订单ID：** ").append(safeOrderUuid(order));

        List<String> productLines = extractProductPriceLines(detail);
        if (!productLines.isEmpty()) {
            content.append("\n**明细价格：** ").append(String.join(" / ", productLines));
        }

        Map<String, Object> header = new HashMap<>();
        header.put("title", Map.of("tag", "plain_text", "content", "iBox 待支付订单提醒"));
        header.put("template", "orange");

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
     * 拉取指定账号的待支付订单列表。
     *
     * @param account iBox账号
     * @return 待支付订单列表；拉取失败时返回空列表
     */
    private List<OrderRes> fetchPendingOrders(IboxAccountEntity account) {
        // 只查询待支付订单(orderStatus=0)，后续再在本地做商品和价格双重匹配。
        String url = String.format(UrlConstant.ORDERS_URL, 1, PAGE_SIZE);
        IboxApiResponse<IboxPageDataRes<OrderRes>> response = iboxClient.get(account.getBearerToken(), url, null, new TypeReference<>() {
        });
        if (response == null || response.getData() == null || response.getData().getList() == null) {
            log.info("【待支付自动付款】未获取到待支付订单，accountId:{}, response:{}", account.getId(), JSONObject.toJSONString(response));
            return Collections.emptyList();
        }
        return response.getData().getList();
    }

    /**
     * 对单个待支付订单做任务归属匹配和价格校验。
     * 只有图片和价格都命中购买任务时，才允许进入付款流程。
     *
     * @param account iBox账号
     * @param order   待支付订单
     */
    private void processOrder(IboxAccountEntity account, OrderRes order) {
        // 订单里没有图片，就无法和购买任务的图片映射做关联确认。
        String picUrl = extractPicUrl(order);
        if (picUrl == null || picUrl.isBlank()) {
            log.info("【待支付自动付款】订单缺少图片地址，accountId:{}, orderUuid:{}", account.getId(), safeOrderUuid(order));
            return;
        }

        // 第一步：先通过订单图片地址命中购买任务对应的商品 ID。
        // 这是当前待支付订单和购买任务之间最稳定的关联方式。
        Long groupId = BuyConstant.picMap.get(picUrl);
        if (groupId == null) {
            log.debug("【待支付自动付款】图片未命中购买任务映射，accountId:{}, orderUuid:{}, picUrl:{}", account.getId(), safeOrderUuid(order), picUrl);
            return;
        }

        // 第二步：命中商品 ID 后，再拿购买任务里记录的目标价格做二次确认。
        // 这样可以避免误把同图但非当前任务目标价格的订单自动付掉。
        BigDecimal targetPrice = BuyConstant.buyMap.get(groupId);
        if (targetPrice == null) {
            log.info("【待支付自动付款】商品已命中图片映射，但未找到目标价格，accountId:{}, orderUuid:{}, groupId:{}", account.getId(), safeOrderUuid(order), groupId);
            return;
        }

        BigDecimal orderPrice = order.getPrice();
        if (orderPrice == null || orderPrice.compareTo(targetPrice) > 0) {
            // 价格不一致时只告警不付款，避免把非任务订单当成目标订单支付掉。
            String dedupKey = "pending-order-price-mismatch:" + account.getId() + ":" + safeOrderUuid(order);
            String message = "iBox待支付订单价格不一致，已跳过自动付款。" + "账号ID：" + account.getId() + "，账号名称：" + safeText(account.getAccountName()) + "，订单ID：" + safeOrderUuid(order) + "，图片地址：" + picUrl + "，商品ID：" + groupId + "，订单价格：" + safePrice(orderPrice) + "，任务价格：" + safePrice(targetPrice);
            feishuMessageUtil.sendText(message, dedupKey, 3);
            log.warn("【待支付自动付款】价格不一致，accountId:{}, orderUuid:{}, groupId:{}, orderPrice:{}, targetPrice:{}", account.getId(), safeOrderUuid(order), groupId, orderPrice, targetPrice);
            return;
        }

        // 只有订单临近过期时才自动支付，避免过早占用资金。
        if (!shouldAutoPayNow(account, order)) {
            return;
        }
        payPendingOrder(account, order);
    }

    /**
     * 查询订单详情，只有当订单剩余过期时间小于阈值时才允许自动支付。
     *
     * @param account iBox账号
     * @param order   待支付订单
     * @return true 表示可以立即自动支付
     */
    private boolean shouldAutoPayNow(IboxAccountEntity account, OrderRes order) {
        try {
            IboxApiResponse<OrderDetailFullRes> detailResponse = iboxClient.get(
                    account.getBearerToken(),
                    String.format(UrlConstant.ORDER_DETAIL_URL, order.getOrderUuid()),
                    null,
                    OrderDetailFullRes.class
            );
            if (detailResponse == null || detailResponse.getData() == null) {
                log.warn("【待支付自动付款】获取订单详情失败，accountId:{}, orderUuid:{}, response:{}",
                        account.getId(), safeOrderUuid(order), JSONObject.toJSONString(detailResponse));
                return false;
            }

            String expiredAt = detailResponse.getData().getExpiredAt();
            if (expiredAt == null || expiredAt.isBlank()) {
                log.warn("【待支付自动付款】订单详情缺少过期时间，accountId:{}, orderUuid:{}",
                        account.getId(), safeOrderUuid(order));
                return false;
            }

            LocalDateTime expiredTime = LocalDateTime.parse(expiredAt, ORDER_TIME_FORMATTER);
            long remainSeconds = Duration.between(LocalDateTime.now(), expiredTime).getSeconds();
            if (remainSeconds < 0) {
                log.info("【待支付自动付款】订单已过期，跳过支付，accountId:{}, orderUuid:{}, remainSeconds:{}",
                        account.getId(), safeOrderUuid(order), remainSeconds);
                return false;
            }
            if (remainSeconds >= AUTO_PAY_EXPIRE_THRESHOLD_SECONDS) {
                log.info("【待支付自动付款】订单未到自动支付窗口，暂不支付，accountId:{}, orderUuid:{}, remainSeconds:{}",
                        account.getId(), safeOrderUuid(order), remainSeconds);
                return false;
            }
            log.info("【待支付自动付款】订单进入自动支付窗口，accountId:{}, orderUuid:{}, remainSeconds:{}",
                    account.getId(), safeOrderUuid(order), remainSeconds);
            return true;
        } catch (DateTimeParseException e) {
            log.warn("【待支付自动付款】订单过期时间解析失败，accountId:{}, orderUuid:{}",
                    account.getId(), safeOrderUuid(order), e);
            return false;
        } catch (Exception e) {
            log.warn("【待支付自动付款】查询订单详情异常，accountId:{}, orderUuid:{}",
                    account.getId(), safeOrderUuid(order), e);
            return false;
        }
    }

    /**
     * 对已通过校验的待支付订单发起真实付款。
     * 流程包含：获取支付链接、提取 uuid、汇付状态确认、余额支付。
     *
     * @param account iBox账号
     * @param order   待支付订单
     */
    private void payPendingOrder(IboxAccountEntity account, OrderRes order) {
        try {
            // 先拿订单支付链接，再从链接里提取 uuid，后续汇付接口都依赖这个 uuid。
            IboxApiResponse<OrderPaymentInfoRes> paymentInfoResponse = iboxClient.get(account.getBearerToken(), String.format(UrlConstant.ORDER_PAYMENT_INFO_URL, order.getOrderUuid(), 0), null, OrderPaymentInfoRes.class);
            if (paymentInfoResponse == null || paymentInfoResponse.getData() == null) {
                log.warn("【待支付自动付款】获取支付信息失败，accountId:{}, orderUuid:{}, response:{}", account.getId(), safeOrderUuid(order), JSONObject.toJSONString(paymentInfoResponse));
                return;
            }

            String uuid = HfPayBalancePayUtil.extractUuidFromLink(paymentInfoResponse.getData().getLink());
            if (uuid == null || uuid.isBlank()) {
                log.warn("【待支付自动付款】支付链接未提取到uuid，accountId:{}, orderUuid:{}, paymentInfo:{}", account.getId(), safeOrderUuid(order), JSONObject.toJSONString(paymentInfoResponse.getData()));
                return;
            }

            // 汇付余额支付前先做状态确认，再真正发起付款。
            String verifyResponse = HfPayBalancePayUtil.transverifyquery(uuid);
            String payResponse = HfPayBalancePayUtil.balancePay(uuid, autoPayPassword);
            String successMessage = "iBox待支付订单自动付款成功，账号ID：" + account.getId() + "，账号名称：" + safeText(account.getAccountName()) + "，订单ID：" + safeOrderUuid(order) + "，订单价格：" + safePrice(order.getPrice());
            feishuMessageUtil.sendText(successMessage, "pending-order-pay-success:" + account.getId() + ":" + safeOrderUuid(order), 3);
            log.info("【待支付自动付款】自动付款成功，accountId:{}, orderUuid:{}, verifyResponse:{}, payResponse:{}", account.getId(), safeOrderUuid(order), verifyResponse, payResponse);
        } catch (Exception e) {
            String message = "iBox待支付订单自动付款失败，账号ID：" + account.getId() + "，账号名称：" + safeText(account.getAccountName()) + "，订单ID：" + safeOrderUuid(order) + "，原因：" + e.getMessage();
            feishuMessageUtil.sendText(message, "pending-order-pay-fail:" + account.getId() + ":" + safeOrderUuid(order), 3);
            log.error("【待支付自动付款】自动付款异常，accountId:{}, orderUuid:{}", account.getId(), safeOrderUuid(order), e);
        }
    }

    /**
     * 从订单预览信息中提取封面图片地址。
     *
     * @param order 待支付订单
     * @return 封面图地址；缺失时返回 null
     */
    private String extractPicUrl(OrderRes order) {
        // 待支付订单当前只依赖 productPreview.coverPicUrl 来做任务归属匹配。
        if (order == null || order.getProductPreview() == null) {
            return null;
        }
        return order.getProductPreview().getCoverPicUrl();
    }

    /**
     * 安全获取订单号，避免日志中出现空指针。
     *
     * @param order 待支付订单
     * @return 订单号；缺失时返回 "-"
     */
    private String safeOrderUuid(OrderRes order) {
        return order == null || order.getOrderUuid() == null ? "-" : order.getOrderUuid();
    }

    /**
     * 安全格式化价格文本，便于日志和通知展示。
     *
     * @param value 价格
     * @return 可展示的价格文本；缺失时返回 "-"
     */
    private String safePrice(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 安全获取订单预览名称，避免通知中出现空指针。
     *
     * @param order 待支付订单
     * @return 藏品名称；缺失时返回 "-"
     */
    private String safeOrderName(OrderRes order) {
        if (order == null || order.getProductPreview() == null || order.getProductPreview().getName() == null || order.getProductPreview().getName().isBlank()) {
            return "-";
        }
        return order.getProductPreview().getName();
    }

    /**
     * 优先使用订单详情中的藏品名，避免列表预览字段为空时通知不完整。
     */
    private String resolveOrderName(OrderRes order, OrderDetailRes detail) {
        if (detail != null && detail.getProductPreview() != null && detail.getProductPreview().getName() != null && !detail.getProductPreview().getName().isBlank()) {
            return detail.getProductPreview().getName();
        }
        return safeOrderName(order);
    }

    /**
     * 优先展示订单详情总价，详情缺失时回退到列表价格。
     */
    private String resolveOrderPrice(OrderRes order, OrderDetailRes detail) {
        if (detail != null && detail.getPrice() != null) {
            return safePrice(detail.getPrice());
        }
        return order == null ? "-" : safePrice(order.getPrice());
    }

    /**
     * 安全读取订单数量。
     */
    private String resolveOrderQuantity(OrderDetailRes detail) {
        return detail == null || detail.getQuantity() == null ? "-" : String.valueOf(detail.getQuantity());
    }

    /**
     * 提取订单明细中的逐件价格，方便快速判断是否是预期订单。
     */
    private List<String> extractProductPriceLines(OrderDetailRes detail) {
        if (detail == null || detail.getProducts() == null || detail.getProducts().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        for (OrderDetailRes.ProductItem product : detail.getProducts()) {
            if (product == null) {
                continue;
            }
            String name = product.getName() == null || product.getName().isBlank() ? "商品" : product.getName();
            lines.add(name + ":" + safePrice(product.getPrice()));
        }
        return lines;
    }

    /**
     * 安全文本兜底，避免通知内容出现 null 或空白。
     *
     * @param value 原始文本
     * @return 可展示文本；缺失时返回 "-"
     */
    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
