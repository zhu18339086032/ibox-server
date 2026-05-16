package com.ibox.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.common.IboxAccountCache;
import com.ibox.common.PurchaseTaskRuntimeManager;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.PurchaseOrderEntity;
import com.ibox.entity.PurchaseTaskEntity;
import com.ibox.manager.PurchaseTaskManager;
import com.ibox.model.ProxyInfo;
import com.ibox.model.req.ibox.BatchPurchaseConsignmentOrdersReq;
import com.ibox.model.req.web.PurchaseTaskPageReq;
import com.ibox.model.req.web.PurchaseTaskSaveReq;
import com.ibox.model.req.web.PurchaseTaskUpdateReq;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.OrderPaymentInfoRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.PurchaseTaskRes;
import com.ibox.service.IboxAccountService;
import com.ibox.service.IboxProxyService;
import com.ibox.service.PurchaseOrderService;
import com.ibox.service.PurchaseTaskService;
import com.ibox.utils.FeishuMessageUtil;
import com.ibox.utils.HfPayBalancePayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 批量购买任务服务实现类。
 */
@Slf4j
@Service
public class PurchaseTaskManagerImpl implements PurchaseTaskManager {

    /**
     * 每个账号每分钟固定允许 10 次有效请求。
     */
    private static final int REQUESTS_PER_ACCOUNT_PER_MINUTE = 10;

    /**
     * 单次批量购买请求固定只尝试购买 1 个。
     */
    private static final int SINGLE_REQUEST_MAX_COUNT = 1;

    /**
     * 记录每个任务当前轮换到的账号下标。
     */
    private final Map<Long, AtomicInteger> taskAccountIndexMap = new ConcurrentHashMap<>();

    @Autowired
    private PurchaseTaskService purchaseTaskService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private IboxAccountService iboxAccountService;

    @Autowired
    private IboxAccountCache iboxAccountCache;

    @Autowired
    private IboxClient iboxClient;

    @Autowired
    private FeishuMessageUtil feishuMessageUtil;

    @Autowired
    private PurchaseTaskRuntimeManager purchaseTaskRuntimeManager;

    @Autowired
    private IboxProxyService iboxProxyService;

    @Override
    public PageResponse<PurchaseTaskRes> list(PurchaseTaskPageReq req) {
        LambdaQueryWrapper<PurchaseTaskEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(req.getTaskName()), PurchaseTaskEntity::getTaskName, req.getTaskName());
        queryWrapper.eq(req.getStatus() != null, PurchaseTaskEntity::getStatus, req.getStatus());
        queryWrapper.eq(req.getRunning() != null, PurchaseTaskEntity::getRunning, req.getRunning());
        queryWrapper.apply(req.getAccountId() != null, "FIND_IN_SET({0}, account_ids)", req.getAccountId());
        queryWrapper.orderByDesc(PurchaseTaskEntity::getId);
        IPage<PurchaseTaskEntity> page = purchaseTaskService.page(new Page<>(req.getPageNum(), req.getPageSize()), queryWrapper);
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::buildRes).toList());
    }

    @Override
    public PurchaseTaskRes getById(Long id) {
        PurchaseTaskEntity entity = purchaseTaskService.getById(id);
        return entity == null ? null : buildRes(entity);
    }

    @Override
    public boolean save(PurchaseTaskSaveReq req) {
        PurchaseTaskEntity entity = new PurchaseTaskEntity();
        BeanUtils.copyProperties(req, entity);
        entity.setAccountIds(joinAccountIds(req.getAccountIds()));
        entity.setTaskType(2);
        entity.setRunning(0);
        entity.setPurchasedCount(0);
        return purchaseTaskService.save(entity);
    }

    @Override
    public boolean updateById(PurchaseTaskUpdateReq req) {
        PurchaseTaskEntity entity = new PurchaseTaskEntity();
        BeanUtils.copyProperties(req, entity);
        entity.setAccountIds(joinAccountIds(req.getAccountIds()));
        taskAccountIndexMap.remove(req.getId());
        return purchaseTaskService.updateById(entity);
    }

    @Override
    public boolean removeById(Long id) {
        purchaseTaskRuntimeManager.stop(id);
        taskAccountIndexMap.remove(id);
        PurchaseTaskEntity entity = new PurchaseTaskEntity();
        entity.setId(id);
        entity.setRunning(0);
        purchaseTaskService.updateById(entity);
        return purchaseTaskService.removeById(id);
    }

    @Override
    public MonitorTaskExecuteRes startById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        PurchaseTaskEntity task = purchaseTaskService.getById(id);
        if (task == null) {
            res.setSuccess(false);
            res.setRunning(false);
            res.setMessage("批量购买任务不存在");
            res.setEndTime(LocalDateTime.now());
            return res;
        }

        if (task.getStatus() == null || task.getStatus() != 1) {
            res.setSuccess(false);
            res.setRunning(false);
            res.setMessage("批量购买任务未启用");
            res.setEndTime(LocalDateTime.now());
            return res;
        }

        if (CollectionUtils.isEmpty(parseAccountIds(task.getAccountIds()))) {
            res.setSuccess(false);
            res.setRunning(false);
            res.setMessage("批量购买任务未配置可用账号");
            res.setEndTime(LocalDateTime.now());
            return res;
        }

        if (isTargetReached(task)) {
            res.setSuccess(false);
            res.setRunning(false);
            res.setMessage("批量购买任务已达到目标购买数");
            res.setEndTime(LocalDateTime.now());
            return res;
        }

        taskAccountIndexMap.put(id, new AtomicInteger(0));
        boolean started = purchaseTaskRuntimeManager.start(id, () -> executeTaskLoop(task.getId()));
        if (started) {
            PurchaseTaskEntity update = new PurchaseTaskEntity();
            update.setId(id);
            update.setRunning(1);
            purchaseTaskService.updateById(update);
            res.setSuccess(true);
            res.setRunning(true);
            res.setMessage("批量购买任务已启动");
        } else {
            res.setSuccess(false);
            res.setRunning(true);
            res.setMessage("批量购买任务已在运行中");
        }
        res.setEndTime(LocalDateTime.now());
        return res;
    }

    @Override
    public MonitorTaskExecuteRes stopById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        boolean stopped = purchaseTaskRuntimeManager.stop(id);
        taskAccountIndexMap.remove(id);
        PurchaseTaskEntity update = new PurchaseTaskEntity();
        update.setId(id);
        update.setRunning(0);
        purchaseTaskService.updateById(update);

        res.setSuccess(stopped);
        res.setRunning(false);
        res.setMessage(stopped ? "批量购买任务已停止" : "批量购买任务未在运行");
        res.setEndTime(LocalDateTime.now());
        return res;
    }

    /**
     * 每轮需要拿到“账号数 * 10”次有效请求。
     * 只有真正拿到 iBox 业务响应的请求才计入分钟额度，代理超时、异常、null 响应都不计数。
     */
    private void executeTaskLoop(Long taskId) {
        PurchaseTaskEntity task = purchaseTaskService.getById(taskId);
        if (task == null) {
            taskAccountIndexMap.remove(taskId);
            purchaseTaskRuntimeManager.stop(taskId);
            return;
        }
        if (task.getStatus() == null || task.getStatus() != 1) {
            stopTask(taskId);
            return;
        }
        if (isTargetReached(task)) {
            stopTask(taskId);
            return;
        }

        List<Long> accountIds = parseAccountIds(task.getAccountIds());
        if (CollectionUtils.isEmpty(accountIds)) {
            log.warn("【批量购买任务】未配置可用账号，taskId:{}", taskId);
            sleep(1000L);
            return;
        }

        long intervalMillis = normalizeInterval(task.getIntervalMillis());
        int targetValidRequestCount = accountIds.size() * REQUESTS_PER_ACCOUNT_PER_MINUTE;
        long cycleStartMillis = System.currentTimeMillis();
        int validRequestCount = 0;
        int purchasedCount = task.getPurchasedCount() == null ? 0 : task.getPurchasedCount();

        while (validRequestCount < targetValidRequestCount) {
            if (isTargetReached(task.getMaxCount(), purchasedCount)) {
                stopTask(taskId);
                return;
            }

            IboxAccountEntity account = getNextAccount(taskId, accountIds);
            if (account == null) {
                log.warn("【批量购买任务】本轮未获取到有效账号，taskId:{}, accountIds:{}", taskId, task.getAccountIds());
                sleep(intervalMillis);
                continue;
            }
            if (!StringUtils.hasText(account.getBearerToken())) {
                log.warn("【批量购买任务】账号Token为空，跳过本次请求，taskId:{}, accountId:{}", taskId, account.getId());
                sleep(intervalMillis);
                continue;
            }

            try {
                PurchaseResult purchaseResult = doPurchase(task, account);
                if (purchaseResult.validRequest) {
                    validRequestCount++;
                }
                if (purchaseResult.success) {
                    purchasedCount++;
                    updatePurchasedCount(taskId, purchasedCount);
                    feishuMessageUtil.sendText("iBox批量购买任务成功，任务名称：" + task.getTaskName()
                            + "，商品名称：" + task.getCollectionName()
                            + "，商品ID：" + task.getDigitalCollectionGroupId()
                            + "，账号ID：" + account.getId()
                            + "，订单ID：" + purchaseResult.orderId);
                    if (isTargetReached(task.getMaxCount(), purchasedCount)) {
                        stopTask(taskId);
                        return;
                    }
                }
            } catch (Exception e) {
                // 单次异常不计入有效请求数，继续补发下一次请求。
                log.warn("【批量购买任务】请求处理异常，taskId:{}, accountId:{}, collectionId:{}", taskId, account.getId(), task.getDigitalCollectionGroupId(), e);
            }

            if (validRequestCount < targetValidRequestCount) {
                sleep(intervalMillis);
            }
        }

        long activeMillis = System.currentTimeMillis() - cycleStartMillis;
        long restMillis = Math.max(60000L - activeMillis, 0L);
        if (restMillis > 0) {
            log.info("【批量购买任务】本轮有效请求完成，taskId:{}, validRequestCount:{}, activeMillis:{}, restMillis:{}",
                    taskId, validRequestCount, activeMillis, restMillis);
            sleep(restMillis);
        }
    }

    /**
     * 执行一次批量购买请求。
     */
    private PurchaseResult doPurchase(PurchaseTaskEntity task, IboxAccountEntity account) {
        BatchPurchaseConsignmentOrdersReq req = buildBatchPurchaseRequest(task);
        ProxyInfo proxyInfo = iboxProxyService.getNextConfigProxy();
        IboxApiResponse<Map<String, Object>> purchaseResponse;
        try {
            purchaseResponse = iboxClient.post(
                    account.getBearerToken(),
                    String.format(UrlConstant.BATCH_PURCHASE_CONSIGNMENT_ORDER_URL, account.getIboxUid()),
                    req,
                    new TypeReference<>() {
                    }
//                    , proxyInfo
            );
        } catch (Exception e) {
            log.warn("【批量购买任务】请求异常，不计入有效请求，taskId:{}, accountId:{}, collectionId:{}",
                    task.getId(), account.getId(), task.getDigitalCollectionGroupId(), e);
            return PurchaseResult.invalid();
        }

        log.info("【批量购买任务】响应结果，taskId:{}, accountId:{}, result:{}", task.getId(), account.getId(), JSONObject.toJSONString(purchaseResponse));
        if (purchaseResponse == null || purchaseResponse.getCode() == null) {
            log.warn("【批量购买任务】响应为空或缺少业务码，不计入有效请求，taskId:{}, accountId:{}",
                    task.getId(), account.getId());
            return PurchaseResult.invalid();
        }

        if (!isPurchaseSuccess(purchaseResponse)) {
            // 只要拿到了明确的业务响应，就算一次有效请求，即使是“暂无可购买”。
            return PurchaseResult.validFail();
        }

        String orderId = String.valueOf(purchaseResponse.getData().get("orderId"));
        saveSuccessOrder(task, account, req, purchaseResponse, orderId);
        logOrderPaymentLink(account, orderId);
        return PurchaseResult.success(orderId);
    }

    /**
     * 构建单次批量购买请求。
     */
    private BatchPurchaseConsignmentOrdersReq buildBatchPurchaseRequest(PurchaseTaskEntity task) {
        BatchPurchaseConsignmentOrdersReq req = new BatchPurchaseConsignmentOrdersReq();
        req.setDigitalCollectionGroupId(task.getDigitalCollectionGroupId());
        req.setMaxCount(SINGLE_REQUEST_MAX_COUNT);
        req.setMaxSinglePrice(task.getMaxSinglePrice() == null ? 0 : task.getMaxSinglePrice().intValue());
        req.setPaymentPlatformCode(task.getPaymentPlatformCode() == null ? 30 : task.getPaymentPlatformCode());
        req.setLevel(-1);
        return req;
    }

    /**
     * 购买成功必须同时满足：code == 0 且存在 orderId。
     */
    private boolean isPurchaseSuccess(IboxApiResponse<Map<String, Object>> purchaseResponse) {
        return purchaseResponse != null
                && purchaseResponse.getCode() != null
                && purchaseResponse.getCode() == 0
                && purchaseResponse.getData() != null
                && purchaseResponse.getData().get("orderId") != null;
    }

    /**
     * 仅在购买成功时保存订单记录。
     */
    private void saveSuccessOrder(PurchaseTaskEntity task, IboxAccountEntity account, BatchPurchaseConsignmentOrdersReq req, IboxApiResponse<Map<String, Object>> purchaseResponse, String orderId) {
        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setTaskId(task.getId());
        order.setAccountId(account.getId());
        order.setDigitalCollectionId(task.getDigitalCollectionGroupId());
        order.setRequestType(2);
        order.setRequestPayload(JSONObject.toJSONString(req));
        order.setResponsePayload(JSONObject.toJSONString(purchaseResponse));
        order.setIboxOrderId(orderId);
        order.setStatus(1);
        order.setSubmittedAt(LocalDateTime.now());
        order.setFinishedAt(LocalDateTime.now());
        purchaseOrderService.save(order);
    }

    /**
     * 购买成功后查询待支付订单信息，并打印支付链接。
     */
    private void logOrderPaymentLink(IboxAccountEntity account, String orderId) {
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
                log.warn("【批量购买任务】获取订单支付信息失败，orderId:{}, response:{}", orderId, JSONObject.toJSONString(paymentInfoResponse));
                return;
            }
            log.info("【批量购买任务】待支付订单链接，orderId:{}, paymentPlatformCode:{}, link:{}",
                    orderId,
                    paymentInfoResponse.getData().getPaymentPlatformCode(),
                    paymentInfoResponse.getData().getLink());
            String payBody = HfPayBalancePayUtil.balancePay(paymentInfoResponse.getData().getLink(), "347364");
            log.info("【批量购买任务】支付结果：{}", payBody);
        } catch (Exception e) {
            log.warn("【批量购买任务】获取订单支付信息异常，orderId:{}", orderId, e);
        }
    }

    /**
     * 判断任务是否已经达到目标购买数。
     */
    private boolean isTargetReached(PurchaseTaskEntity task) {
        return isTargetReached(task.getMaxCount(), task.getPurchasedCount() == null ? 0 : task.getPurchasedCount());
    }

    /**
     * 根据目标总数和已购买数判断任务是否完成。
     */
    private boolean isTargetReached(Integer maxCount, int purchasedCount) {
        int targetCount = maxCount == null ? 0 : maxCount;
        return targetCount > 0 && purchasedCount >= targetCount;
    }

    /**
     * 更新任务已购买数。
     */
    private void updatePurchasedCount(Long taskId, int purchasedCount) {
        PurchaseTaskEntity update = new PurchaseTaskEntity();
        update.setId(taskId);
        update.setPurchasedCount(purchasedCount);
        purchaseTaskService.updateById(update);
    }

    /**
     * 将任务状态置为停止，并终止后台循环。
     */
    private void stopTask(Long taskId) {
        PurchaseTaskEntity update = new PurchaseTaskEntity();
        update.setId(taskId);
        update.setRunning(0);
        purchaseTaskService.updateById(update);
        taskAccountIndexMap.remove(taskId);
        purchaseTaskRuntimeManager.stop(taskId);
    }

    /**
     * 从任务配置的账号列表中按顺序轮换选择本次账号。
     */
    private IboxAccountEntity getNextAccount(Long taskId, List<Long> accountIds) {
        if (CollectionUtils.isEmpty(accountIds)) {
            return null;
        }
        AtomicInteger accountIndex = taskAccountIndexMap.computeIfAbsent(taskId, key -> new AtomicInteger(0));
        int startIndex = Math.floorMod(accountIndex.getAndIncrement(), accountIds.size());
        for (int offset = 0; offset < accountIds.size(); offset++) {
            Long accountId = accountIds.get((startIndex + offset) % accountIds.size());
            IboxAccountEntity account = getAccount(accountId);
            if (account != null) {
                return account;
            }
        }
        return null;
    }

    /**
     * 先查缓存，缓存未命中时再查数据库。
     */
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

    /**
     * 将账号ID列表转换为逗号分隔字符串，并去重和过滤空值。
     */
    private String joinAccountIds(List<Long> accountIds) {
        if (CollectionUtils.isEmpty(accountIds)) {
            return null;
        }
        return accountIds.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), values -> String.join(",", values)));
    }

    /**
     * 将逗号分隔的账号ID字符串解析为列表，保留原有顺序。
     */
    private List<Long> parseAccountIds(String accountIds) {
        if (!StringUtils.hasText(accountIds)) {
            return new ArrayList<>();
        }
        return Arrays.stream(accountIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .map(value -> {
                    try {
                        return Long.valueOf(value);
                    } catch (NumberFormatException e) {
                        log.warn("【批量购买任务】解析账号ID失败，value:{}", value);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 将账号ID列表转换为账号名称列表，便于前端直接展示。
     */
    private List<String> buildAccountNames(List<Long> accountIds) {
        if (CollectionUtils.isEmpty(accountIds)) {
            return new ArrayList<>();
        }
        return accountIds.stream()
                .map(this::getAccount)
                .filter(Objects::nonNull)
                .map(account -> StringUtils.hasText(account.getPhone())
                        ? account.getAccountName() + "(" + account.getPhone() + ")"
                        : account.getAccountName() + "(ID:" + account.getId() + ")")
                .toList();
    }

    /**
     * 统一处理休眠，避免线程被中断后继续执行。
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(millis, 0L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 单个任务响应对象构建。
     */
    private PurchaseTaskRes buildRes(PurchaseTaskEntity entity) {
        PurchaseTaskRes res = new PurchaseTaskRes();
        BeanUtils.copyProperties(entity, res);
        List<Long> accountIds = parseAccountIds(entity.getAccountIds());
        res.setAccountIds(accountIds);
        res.setAccountNames(buildAccountNames(accountIds));
        return res;
    }

    /**
     * 一次购买执行的返回结果。
     */
    private static class PurchaseResult {
        private final boolean success;
        private final boolean validRequest;
        private final String orderId;

        private PurchaseResult(boolean success, boolean validRequest, String orderId) {
            this.success = success;
            this.validRequest = validRequest;
            this.orderId = orderId;
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

    /**
     * 将任务间隔归一化，避免出现 0 或负值导致调度异常。
     */
    private long normalizeInterval(Long intervalMillis) {
        return intervalMillis == null || intervalMillis <= 0 ? 1000L : intervalMillis;
    }
}
