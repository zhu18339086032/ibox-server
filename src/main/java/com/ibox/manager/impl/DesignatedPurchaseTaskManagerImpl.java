package com.ibox.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.common.DesignatedPurchaseTaskRuntimeManager;
import com.ibox.common.IboxAccountCache;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.DesignatedPurchaseTaskEntity;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.PurchaseOrderEntity;
import com.ibox.manager.DesignatedPurchaseTaskManager;
import com.ibox.model.req.ibox.PurchaseConsignmentOrdersReq;
import com.ibox.model.req.web.DesignatedPurchaseTaskPageReq;
import com.ibox.model.req.web.DesignatedPurchaseTaskSaveReq;
import com.ibox.model.req.web.DesignatedPurchaseTaskUpdateReq;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.OrderPaymentInfoRes;
import com.ibox.model.res.ibox.UserDigitalCollectionGroupPageRes;
import com.ibox.model.res.ibox.UserDigitalCollectionItemRes;
import com.ibox.model.res.web.DesignatedPurchaseTaskRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.service.DesignatedPurchaseTaskService;
import com.ibox.service.IboxAccountService;
import com.ibox.service.PurchaseOrderService;
import com.ibox.utils.FeishuMessageUtil;
import com.ibox.utils.HfPayBalancePayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指定购买任务实现
 */
@Slf4j
@Service
public class DesignatedPurchaseTaskManagerImpl implements DesignatedPurchaseTaskManager {

    private static final int STATUS_ENABLED = 1;

    private static final int PAGE_SIZE = 20;

    private static final int PURCHASE_LOCKED_CODE = 2600009;

    private static final long LOCKED_ITEM_COOLDOWN_MILLIS = 15000L;

    /**
     * 查询目标用户某个商品下的持仓列表。
     * 这里不扫全市场，只盯指定用户的指定商品。
     */
    private static final String TARGET_USER_DIGITAL_COLLECTION_GROUP_URL =
            UrlConstant.SAIL_API_BASE_URL + "/personal-center-service/users/%s/digital-collection-groups/%s?pageNo=%s&pageSize=%s";

    private final Map<String, Long> lockedItemCooldownMap = new ConcurrentHashMap<>();

    @Autowired
    private DesignatedPurchaseTaskService designatedPurchaseTaskService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private IboxAccountService iboxAccountService;

    @Autowired
    private FeishuMessageUtil feishuMessageUtil;

    @Autowired
    private IboxAccountCache iboxAccountCache;

    @Autowired
    private IboxClient iboxClient;

    @Autowired
    private DesignatedPurchaseTaskRuntimeManager designatedPurchaseTaskRuntimeManager;

    @Value("${ibox.auto-pay.password:}")
    private String autoPayPassword;

    @Override
    public PageResponse<DesignatedPurchaseTaskRes> list(DesignatedPurchaseTaskPageReq req) {
        LambdaQueryWrapper<DesignatedPurchaseTaskEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(req.getTaskName()), DesignatedPurchaseTaskEntity::getTaskName, req.getTaskName());
        queryWrapper.eq(req.getAccountId() != null, DesignatedPurchaseTaskEntity::getAccountId, req.getAccountId());
        queryWrapper.eq(req.getTargetUserId() != null, DesignatedPurchaseTaskEntity::getTargetUserId, req.getTargetUserId());
        queryWrapper.eq(req.getStatus() != null, DesignatedPurchaseTaskEntity::getStatus, req.getStatus());
        queryWrapper.eq(req.getRunning() != null, DesignatedPurchaseTaskEntity::getRunning, req.getRunning());
        queryWrapper.orderByDesc(DesignatedPurchaseTaskEntity::getId);
        IPage<DesignatedPurchaseTaskEntity> page = designatedPurchaseTaskService.page(new Page<>(req.getPageNum(), req.getPageSize()), queryWrapper);
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::buildRes).toList());
    }

    @Override
    public DesignatedPurchaseTaskRes getById(Long id) {
        DesignatedPurchaseTaskEntity entity = designatedPurchaseTaskService.getById(id);
        return entity == null ? null : buildRes(entity);
    }

    @Override
    public boolean save(DesignatedPurchaseTaskSaveReq req) {
        validateTaskReq(req);
        DesignatedPurchaseTaskEntity entity = new DesignatedPurchaseTaskEntity();
        BeanUtils.copyProperties(req, entity);
        entity.setPaymentPlatformCode(normalizePaymentPlatformCode(req.getPaymentPlatformCode()));
        entity.setIntervalMillis(normalizeInterval(req.getIntervalMillis()));
        entity.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        entity.setRunning(0);
        entity.setPurchasedCount(0);
        return designatedPurchaseTaskService.save(entity);
    }

    @Override
    public boolean updateById(DesignatedPurchaseTaskUpdateReq req) {
        validateTaskReq(req);
        DesignatedPurchaseTaskEntity entity = new DesignatedPurchaseTaskEntity();
        entity.setId(req.getId());
        entity.setTaskName(req.getTaskName());
        entity.setAccountId(req.getAccountId());
        entity.setTargetUserId(req.getTargetUserId());
        entity.setDigitalCollectionGroupId(req.getDigitalCollectionGroupId());
        entity.setCollectionName(req.getCollectionName());
        entity.setMaxCount(req.getMaxCount());
        entity.setMaxSinglePrice(req.getMaxSinglePrice());
        entity.setIntervalMillis(normalizeInterval(req.getIntervalMillis()));
        entity.setPaymentPlatformCode(normalizePaymentPlatformCode(req.getPaymentPlatformCode()));
        entity.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        entity.setRunning(req.getRunning());
        entity.setRemark(req.getRemark());
        return designatedPurchaseTaskService.updateById(entity);
    }

    @Override
    public boolean removeById(Long id) {
        designatedPurchaseTaskRuntimeManager.stop(id);
        DesignatedPurchaseTaskEntity update = new DesignatedPurchaseTaskEntity();
        update.setId(id);
        update.setRunning(0);
        designatedPurchaseTaskService.updateById(update);
        return designatedPurchaseTaskService.removeById(id);
    }

    @Override
    public MonitorTaskExecuteRes startById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        DesignatedPurchaseTaskEntity task = designatedPurchaseTaskService.getById(id);
        if (task == null) {
            return buildExecuteRes(res, false, false, "任务不存在");
        }
        if (task.getStatus() == null || task.getStatus() != STATUS_ENABLED) {
            return buildExecuteRes(res, false, false, "任务未启用");
        }
        if (isTargetReached(task)) {
            return buildExecuteRes(res, false, false, "任务已达到目标购买数");
        }

        IboxAccountEntity account = getAccount(task.getAccountId());
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            return buildExecuteRes(res, false, false, "账号不存在或 token 为空");
        }

        boolean started = designatedPurchaseTaskRuntimeManager.start(id, () -> executeTaskLoop(id));
        if (!started) {
            return buildExecuteRes(res, false, true, "任务已在运行中");
        }

        DesignatedPurchaseTaskEntity update = new DesignatedPurchaseTaskEntity();
        update.setId(id);
        update.setRunning(1);
        designatedPurchaseTaskService.updateById(update);
        return buildExecuteRes(res, true, true, "任务已启动");
    }

    @Override
    public MonitorTaskExecuteRes stopById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        boolean stopped = designatedPurchaseTaskRuntimeManager.stop(id);
        DesignatedPurchaseTaskEntity update = new DesignatedPurchaseTaskEntity();
        update.setId(id);
        update.setRunning(0);
        designatedPurchaseTaskService.updateById(update);
        return buildExecuteRes(res, stopped, false, stopped ? "任务已停止" : "任务未运行");
    }

    /**
     * 指定购买主循环。
     * 每轮都重新查目标用户指定商品的在售明细，命中后直接走快速购买同款接口。
     */
    private void executeTaskLoop(Long taskId) {
        if (shouldStop(taskId)) {
            return;
        }

        DesignatedPurchaseTaskEntity task = designatedPurchaseTaskService.getById(taskId);
        if (task == null) {
            stopTask(taskId);
            return;
        }
        if (task.getStatus() == null || task.getStatus() != STATUS_ENABLED) {
            stopTask(taskId);
            return;
        }
        if (isTargetReached(task)) {
            stopTask(taskId);
            return;
        }

        IboxAccountEntity account = getAccount(task.getAccountId());
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            log.warn("【指定购买任务】账号不存在或 token 为空，taskId:{}, accountId:{}", taskId, task.getAccountId());
            sleep(task.getIntervalMillis());
            return;
        }

        try {
            List<UserDigitalCollectionItemRes> items = fetchTargetUserItems(task, account);
            UserDigitalCollectionItemRes matchedItem = pickPurchasableItem(task, items);
            if (matchedItem == null) {
                log.info("【指定购买任务】未匹配到可购买单子，taskId:{}, targetUserId:{}, groupId:{}", taskId, task.getTargetUserId(), task.getDigitalCollectionGroupId());
                sleep(task.getIntervalMillis());
                return;
            }

            log.info("【指定购买任务】命中可购买单子，taskId:{}, accountId:{}, targetUserId:{}, digitalCollectionId:{}, orderId:{}, price:{}",
                    taskId, account.getId(), task.getTargetUserId(), matchedItem.getId(), matchedItem.getOrderId(), matchedItem.getPrice());
            PurchaseResult purchaseResult = doFastPurchase(task, account, matchedItem);
            if (purchaseResult.success()) {
                int purchasedCount = (task.getPurchasedCount() == null ? 0 : task.getPurchasedCount()) + 1;
                updatePurchasedCount(taskId, purchasedCount);
                if (isTargetReached(task.getMaxCount(), purchasedCount)) {
                    stopTask(taskId);
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("【指定购买任务】执行异常，taskId:{}, accountId:{}, targetUserId:{}, groupId:{}",
                    taskId, task.getAccountId(), task.getTargetUserId(), task.getDigitalCollectionGroupId(), e);
        }

        sleep(task.getIntervalMillis());
    }

    /**
     * 查询目标用户当前商品持仓明细。
     */
    private List<UserDigitalCollectionItemRes> fetchTargetUserItems(DesignatedPurchaseTaskEntity task, IboxAccountEntity account) {
        String url = String.format(
                TARGET_USER_DIGITAL_COLLECTION_GROUP_URL,
                task.getTargetUserId(),
                task.getDigitalCollectionGroupId(),
                1,
                PAGE_SIZE
        );
        IboxApiResponse<UserDigitalCollectionGroupPageRes> response = iboxClient.get(
                account.getBearerToken(),
                url,
                null,
                UserDigitalCollectionGroupPageRes.class
        );
        if (response == null || response.getCode() == null || response.getCode() != 0 || response.getData() == null) {
            log.warn("【指定购买任务】查询目标用户持仓失败，taskId:{}, accountId:{}, targetUserId:{}, response:{}",
                    task.getId(), account.getId(), task.getTargetUserId(), JSONObject.toJSONString(response));
            return List.of();
        }
        return response.getData().getList() == null ? List.of() : response.getData().getList();
    }

    /**
     * 从目标用户商品列表中选择本轮最适合下单的单子。
     * 只接受在售且价格不超过任务上限的项，并优先买更便宜的。
     */
    private UserDigitalCollectionItemRes pickPurchasableItem(DesignatedPurchaseTaskEntity task, List<UserDigitalCollectionItemRes> items) {
        if (CollectionUtils.isEmpty(items)) {
            return null;
        }
        BigDecimal maxPrice = task.getMaxSinglePrice();
        return items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .filter(item -> item.getPrice() != null)
                .filter(item -> !isLockedCooldown(item))
                .filter(item -> item.getConsignmentStatus() != null && item.getConsignmentStatus() == 1)
                .filter(item -> item.getDigitalCollectionStatus() != null)
                .filter(item -> maxPrice == null || item.getPrice().compareTo(maxPrice) <= 0)
                .min(Comparator.comparing(UserDigitalCollectionItemRes::getPrice))
                .orElse(null);
    }

    /**
     * 下单逻辑复用快速购买接口。
     * 指定购买只改“找单来源”，真正下单还是 purchase-consignment-orders。
     */
    private PurchaseResult doFastPurchase(DesignatedPurchaseTaskEntity task, IboxAccountEntity account, UserDigitalCollectionItemRes item) {
        PurchaseConsignmentOrdersReq req = new PurchaseConsignmentOrdersReq();
        req.setDigitalCollectionId(item.getId());
        req.setPaymentPlatformCode(normalizePaymentPlatformCode(task.getPaymentPlatformCode()).longValue());

        IboxApiResponse<Map<String, Object>> purchaseResponse;
        try {
            purchaseResponse = iboxClient.post(account.getBearerToken(), UrlConstant.PURCHASE_CONSIGNMENT_ORDER_URL, req, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("【指定购买任务】下单异常，taskId:{}, accountId:{}, digitalCollectionId:{}", task.getId(), account.getId(), item.getId(), e);
            return PurchaseResult.invalid();
        }

        log.info("【指定购买任务】下单响应，taskId:{}, accountId:{}, digitalCollectionId:{}, result:{}",
                task.getId(), account.getId(), item.getId(), JSONObject.toJSONString(purchaseResponse));
        if (purchaseResponse == null || purchaseResponse.getCode() == null) {
            return PurchaseResult.invalid();
        }
        if (purchaseResponse.getCode() == PURCHASE_LOCKED_CODE) {
            markLockedCooldown(item);
            log.info("【指定购买任务】单子已被锁定，加入冷却过滤，taskId:{}, accountId:{}, digitalCollectionId:{}, orderId:{}, cooldownMs:{}",
                    task.getId(), account.getId(), item.getId(), item.getOrderId(), LOCKED_ITEM_COOLDOWN_MILLIS);
            return PurchaseResult.validFail();
        }
        if (!isPurchaseSuccess(purchaseResponse)) {
            return PurchaseResult.validFail();
        }

        String orderId = String.valueOf(purchaseResponse.getData().get("orderId"));
        saveSuccessOrder(task, account, req, purchaseResponse, orderId, item);
        autoPayOrder(task, account, orderId, item);
        return PurchaseResult.success(orderId);
    }

    private boolean isPurchaseSuccess(IboxApiResponse<Map<String, Object>> purchaseResponse) {
        return purchaseResponse != null
                && purchaseResponse.getCode() != null
                && purchaseResponse.getCode() == 0
                && purchaseResponse.getData() != null
                && purchaseResponse.getData().get("orderId") != null;
    }

    private void saveSuccessOrder(DesignatedPurchaseTaskEntity task,
                                  IboxAccountEntity account,
                                  PurchaseConsignmentOrdersReq req,
                                  IboxApiResponse<Map<String, Object>> purchaseResponse,
                                  String orderId,
                                  UserDigitalCollectionItemRes item) {
        PurchaseOrderEntity purchaseOrder = new PurchaseOrderEntity();
        purchaseOrder.setTaskId(task.getId());
        purchaseOrder.setAccountId(account.getId());
        purchaseOrder.setDigitalCollectionId(item.getId());
        purchaseOrder.setRequestType(1);
        purchaseOrder.setRequestPayload(JSONObject.toJSONString(req));
        purchaseOrder.setResponsePayload(JSONObject.toJSONString(purchaseResponse));
        purchaseOrder.setIboxOrderId(orderId);
        purchaseOrder.setStatus(1);
        purchaseOrder.setSubmittedAt(LocalDateTime.now());
        purchaseOrder.setFinishedAt(LocalDateTime.now());
        purchaseOrderService.save(purchaseOrder);
    }

    private void autoPayOrder(DesignatedPurchaseTaskEntity task, IboxAccountEntity account, String orderId, UserDigitalCollectionItemRes item) {
        if (account == null || !StringUtils.hasText(orderId)) {
            return;
        }
        try {
            IboxApiResponse<OrderPaymentInfoRes> paymentInfoResponse = iboxClient.get(
                    account.getBearerToken(),
                    String.format(UrlConstant.ORDER_PAYMENT_INFO_URL, orderId, 0),
                    null,
                    OrderPaymentInfoRes.class
            );
            if (paymentInfoResponse == null || paymentInfoResponse.getCode() == null || paymentInfoResponse.getCode() != 0 || paymentInfoResponse.getData() == null) {
                feishuMessageUtil.sendText(
                        "iBox指定购买下单成功，但获取支付信息失败。任务ID：" + task.getId() + "，账号ID：" + account.getId() + "，订单ID：" + orderId,
                        "designated-purchase-payment-info-fail:" + orderId,
                        3
                );
                log.warn("【指定购买任务】获取订单支付信息失败，orderId:{}, response:{}", orderId, JSONObject.toJSONString(paymentInfoResponse));
                return;
            }

            if (!StringUtils.hasText(autoPayPassword)) {
                feishuMessageUtil.sendText(
                        "iBox指定购买下单成功，但未配置自动支付密码。任务ID：" + task.getId() + "，账号ID：" + account.getId() + "，订单ID：" + orderId,
                        "designated-purchase-no-password:" + orderId,
                        3
                );
                log.warn("【指定购买任务】未配置自动支付密码，orderId:{}", orderId);
                return;
            }

            String uuid = HfPayBalancePayUtil.extractUuidFromLink(paymentInfoResponse.getData().getLink());
            if (!StringUtils.hasText(uuid)) {
                feishuMessageUtil.sendText(
                        "iBox指定购买下单成功，但支付链接未提取到uuid。任务ID：" + task.getId() + "，账号ID：" + account.getId() + "，订单ID：" + orderId,
                        "designated-purchase-uuid-fail:" + orderId,
                        3
                );
                log.warn("【指定购买任务】支付链接未提取到uuid，orderId:{}, paymentInfo:{}", orderId, JSONObject.toJSONString(paymentInfoResponse.getData()));
                return;
            }

            String verifyResponse = HfPayBalancePayUtil.transverifyquery(uuid);
            String payResponse = HfPayBalancePayUtil.balancePay(uuid, autoPayPassword);
            String successMessage = "iBox指定购买自动付款成功，任务ID：" + task.getId()
                    + "，账号ID：" + account.getId()
                    + "，账号名称：" + safeText(account.getAccountName())
                    + "，订单ID：" + orderId
                    + "，藏品ID：" + (item == null || item.getId() == null ? "-" : item.getId())
                    + "，藏品名称：" + (item == null ? "-" : safeText(item.getName()))
                    + "，价格：" + (item == null ? "-" : safePrice(item.getPrice()));
            feishuMessageUtil.sendText(successMessage, "designated-purchase-pay-success:" + orderId, 3);
            log.info("【指定购买任务】自动付款成功，orderId:{}, verifyResponse:{}, payResponse:{}", orderId, verifyResponse, payResponse);
        } catch (Exception e) {
            String message = "iBox指定购买自动付款失败，任务ID：" + task.getId()
                    + "，账号ID：" + account.getId()
                    + "，账号名称：" + safeText(account.getAccountName())
                    + "，订单ID：" + orderId
                    + "，原因：" + safeText(e.getMessage());
            feishuMessageUtil.sendText(message, "designated-purchase-pay-fail:" + orderId, 3);
            log.warn("【指定购买任务】自动付款异常，orderId:{}", orderId, e);
        }
    }
    private void validateTaskReq(DesignatedPurchaseTaskSaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getTaskName())) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (req.getAccountId() == null) {
            throw new IllegalArgumentException("账号不能为空");
        }
        if (req.getTargetUserId() == null) {
            throw new IllegalArgumentException("目标用户ID不能为空");
        }
        if (req.getDigitalCollectionGroupId() == null) {
            throw new IllegalArgumentException("商品不能为空");
        }
        if (!StringUtils.hasText(req.getCollectionName())) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (req.getMaxCount() == null || req.getMaxCount() <= 0) {
            throw new IllegalArgumentException("购买数量必须大于0");
        }
        if (req.getMaxSinglePrice() == null || req.getMaxSinglePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("最高价格不能为空且不能小于0");
        }
    }

    private MonitorTaskExecuteRes buildExecuteRes(MonitorTaskExecuteRes res, boolean success, boolean running, String message) {
        res.setSuccess(success);
        res.setRunning(running);
        res.setMessage(message);
        res.setEndTime(LocalDateTime.now());
        return res;
    }

    private boolean isTargetReached(DesignatedPurchaseTaskEntity task) {
        return isTargetReached(task.getMaxCount(), task.getPurchasedCount() == null ? 0 : task.getPurchasedCount());
    }

    private boolean isTargetReached(Integer maxCount, int purchasedCount) {
        int targetCount = maxCount == null ? 0 : maxCount;
        return targetCount > 0 && purchasedCount >= targetCount;
    }

    private void updatePurchasedCount(Long taskId, int purchasedCount) {
        DesignatedPurchaseTaskEntity update = new DesignatedPurchaseTaskEntity();
        update.setId(taskId);
        update.setPurchasedCount(purchasedCount);
        designatedPurchaseTaskService.updateById(update);
    }

    private void stopTask(Long taskId) {
        DesignatedPurchaseTaskEntity update = new DesignatedPurchaseTaskEntity();
        update.setId(taskId);
        update.setRunning(0);
        designatedPurchaseTaskService.updateById(update);
        designatedPurchaseTaskRuntimeManager.stop(taskId);
    }

    private boolean shouldStop(Long taskId) {
        return Thread.currentThread().isInterrupted() || !designatedPurchaseTaskRuntimeManager.isRunning(taskId);
    }

    private IboxAccountEntity getAccount(Long accountId) {
        IboxAccountEntity account = iboxAccountCache.getById(accountId);
        if (account != null) {
            return account;
        }
        account = iboxAccountService.getById(accountId);
        if (account != null) {
            iboxAccountCache.put(account);
        }
        return account;
    }

    private long normalizeInterval(Long intervalMillis) {
        return intervalMillis == null || intervalMillis <= 0 ? 1000L : intervalMillis;
    }

    private Integer normalizePaymentPlatformCode(Integer paymentPlatformCode) {
        return paymentPlatformCode == null ? 30 : paymentPlatformCode;
    }

    private void sleep(Long millis) {
        try {
            Thread.sleep(Math.max(normalizeInterval(millis), 0L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private DesignatedPurchaseTaskRes buildRes(DesignatedPurchaseTaskEntity entity) {
        DesignatedPurchaseTaskRes res = new DesignatedPurchaseTaskRes();
        BeanUtils.copyProperties(entity, res);
        IboxAccountEntity account = getAccount(entity.getAccountId());
        if (account != null) {
            res.setAccountName(StringUtils.hasText(account.getPhone())
                    ? account.getAccountName() + "(" + account.getPhone() + ")"
                    : account.getAccountName() + "(ID:" + account.getId() + ")");
        }
        return res;
    }

    private String safeText(String text) {
        return StringUtils.hasText(text) ? text : "-";
    }

    private String safePrice(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private boolean isLockedCooldown(UserDigitalCollectionItemRes item) {
        String key = buildLockedItemKey(item);
        if (!StringUtils.hasText(key)) {
            return false;
        }
        Long expireAt = lockedItemCooldownMap.get(key);
        if (expireAt == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (expireAt <= now) {
            lockedItemCooldownMap.remove(key);
            return false;
        }
        return true;
    }

    private void markLockedCooldown(UserDigitalCollectionItemRes item) {
        String key = buildLockedItemKey(item);
        if (!StringUtils.hasText(key)) {
            return;
        }
        lockedItemCooldownMap.put(key, System.currentTimeMillis() + LOCKED_ITEM_COOLDOWN_MILLIS);
    }

    private String buildLockedItemKey(UserDigitalCollectionItemRes item) {
        if (item == null) {
            return null;
        }
        if (item.getOrderId() != null) {
            return "order:" + item.getOrderId();
        }
        if (item.getId() != null) {
            return "dc:" + item.getId();
        }
        return null;
    }
    private static class PurchaseResult {
        private final boolean success;
        private final boolean validRequest;
        private final String orderId;

        private PurchaseResult(boolean success, boolean validRequest, String orderId) {
            this.success = success;
            this.validRequest = validRequest;
            this.orderId = orderId;
        }

        private boolean success() {
            return success;
        }

        @SuppressWarnings("unused")
        private boolean validRequest() {
            return validRequest;
        }

        @SuppressWarnings("unused")
        private String orderId() {
            return orderId;
        }

        private static PurchaseResult success(String orderId) {
            return new PurchaseResult(true, true, orderId);
        }

        private static PurchaseResult validFail() {
            return new PurchaseResult(false, true, null);
        }

        private static PurchaseResult invalid() {
            return new PurchaseResult(false, false, null);
        }
    }
}
