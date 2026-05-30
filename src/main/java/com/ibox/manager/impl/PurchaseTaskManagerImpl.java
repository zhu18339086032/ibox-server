package com.ibox.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.common.IboxAccountCache;
import com.ibox.common.PurchaseTaskRuntimeManager;
import com.ibox.constants.BuyConstant;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.MarketCollectionEntity;
import com.ibox.entity.PurchaseOrderEntity;
import com.ibox.entity.PurchaseTaskEntity;
import com.ibox.manager.PurchaseTaskManager;
import com.ibox.model.ProxyInfo;
import com.ibox.model.req.ibox.BatchPurchaseConsignmentOrdersReq;
import com.ibox.model.req.ibox.PurchaseConsignmentOrdersReq;
import com.ibox.model.req.web.PurchaseTaskPageReq;
import com.ibox.model.req.web.PurchaseTaskSaveReq;
import com.ibox.model.req.web.PurchaseTaskUpdateReq;
import com.ibox.model.res.ibox.ConsignmentOrderListRes;
import com.ibox.model.res.ibox.ConsignmentOrderRes;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.OrderPaymentInfoRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.PurchaseTaskRes;
import com.ibox.service.*;
import com.ibox.utils.FeishuMessageUtil;
import com.ibox.utils.HfPayBalancePayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
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

    private static final int TASK_TYPE_FAST_PURCHASE = 1;

    private static final int TASK_TYPE_BATCH_PURCHASE = 2;

    /**
     * 每个账号每分钟固定允许 10 次有效请求。
     */
    private static final int REQUESTS_PER_ACCOUNT_PER_MINUTE = 8;

    /**
     * 快速购买任务每个账号每分钟允许的扫描次数。
     */
    private static final int FAST_SCAN_REQUESTS_PER_ACCOUNT_PER_MINUTE = 10;

    /**
     * 快速购买扫描时每页最大条数。
     */
    private static final int FAST_SCAN_PAGE_SIZE = 20;

    /**
     * 单次批量购买请求固定只尝试购买 1 个。
     */
    private static final int SINGLE_REQUEST_MAX_COUNT = 1;

    /**
     * 记录每个任务当前轮换到的账号下标。
     */
    private final Map<Long, AtomicInteger> taskAccountIndexMap = new ConcurrentHashMap<>();

    /**
     * 快速购买任务扫描账号下标。
     */
    private final Map<Long, AtomicInteger> taskFastScanIndexMap = new ConcurrentHashMap<>();

    /**
     * 快速购买任务按账号记录扫描次数窗口。
     */
    private final Map<Long, Map<Long, AccountScanWindow>> taskScanWindowMap = new ConcurrentHashMap<>();

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

    @Autowired
    private MarketCollectionService marketCollectionService;

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
        entity.setTaskType(normalizeTaskType(req.getTaskType()));
        entity.setRunning(0);
        entity.setPurchasedCount(0);
        return purchaseTaskService.save(entity);
    }

    @Override
    public boolean updateById(PurchaseTaskUpdateReq req) {
        PurchaseTaskEntity entity = new PurchaseTaskEntity();
        entity.setId(req.getId());
        entity.setTaskName(req.getTaskName());
        entity.setDigitalCollectionGroupId(req.getDigitalCollectionGroupId());
        entity.setCollectionName(req.getCollectionName());
        entity.setAccountIds(joinAccountIds(req.getAccountIds()));
        entity.setMaxCount(req.getMaxCount());
        entity.setMaxSinglePrice(req.getMaxSinglePrice());
        entity.setIntervalMillis(req.getIntervalMillis());
        entity.setPaymentPlatformCode(req.getPaymentPlatformCode());
        entity.setTaskType(normalizeTaskType(req.getTaskType()));
        entity.setStatus(req.getStatus());
        entity.setRunning(req.getRunning());
        entity.setRemark(req.getRemark());
        taskAccountIndexMap.remove(req.getId());
        return purchaseTaskService.updateById(entity);
    }

    @Override
    public boolean removeById(Long id) {
        purchaseTaskRuntimeManager.stop(id);
        taskAccountIndexMap.remove(id);
        PurchaseTaskEntity task = purchaseTaskService.getById(id);
        PurchaseTaskEntity entity = new PurchaseTaskEntity();
        entity.setId(task.getId());
        entity.setRunning(0);
        purchaseTaskService.updateById(entity);
        BuyConstant.buyMap.remove(task.getDigitalCollectionGroupId(),task.getMaxSinglePrice());
        MarketCollectionEntity marketCollectionEntity = marketCollectionService.getById(task.getDigitalCollectionGroupId());
        BuyConstant.picMap.remove(marketCollectionEntity.getCoverPicUrl(),task.getDigitalCollectionGroupId());
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
            res.setMessage("任务不存在");
            res.setEndTime(LocalDateTime.now());
            return res;
        }

        if (task.getStatus() == null || task.getStatus() != 1) {
            res.setSuccess(false);
            res.setRunning(false);
            res.setMessage("任务未启用");
            res.setEndTime(LocalDateTime.now());
            return res;
        }

        if (CollectionUtils.isEmpty(parseAccountIds(task.getAccountIds()))) {
            res.setSuccess(false);
            res.setRunning(false);
            res.setMessage("任务未配置可用账号");
            res.setEndTime(LocalDateTime.now());
            return res;
        }

        if (isTargetReached(task)) {
            res.setSuccess(false);
            res.setRunning(false);
            res.setMessage("任务已达到目标购买数");
            res.setEndTime(LocalDateTime.now());
            return res;
        }

        taskAccountIndexMap.put(id, new AtomicInteger(0));
        taskScanWindowMap.remove(id);
        BuyConstant.buyMap.put(task.getDigitalCollectionGroupId(),task.getMaxSinglePrice());
        MarketCollectionEntity marketCollectionEntity = marketCollectionService.getById(task.getDigitalCollectionGroupId());
        BuyConstant.picMap.put(marketCollectionEntity.getCoverPicUrl(),task.getDigitalCollectionGroupId());
        boolean started = purchaseTaskRuntimeManager.start(id, () -> executeTaskLoop(task.getId()));
        if (started) {
            PurchaseTaskEntity update = new PurchaseTaskEntity();
            update.setId(id);
            update.setRunning(1);
            purchaseTaskService.updateById(update);
            res.setSuccess(true);
            res.setRunning(true);
            res.setMessage("任务已启动");
        } else {
            res.setSuccess(false);
            res.setRunning(true);
            res.setMessage("任务已在运行中");
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
        taskScanWindowMap.remove(id);
        PurchaseTaskEntity update = new PurchaseTaskEntity();
        update.setId(id);
        update.setRunning(0);

        PurchaseTaskEntity task = purchaseTaskService.getById(id);
        BuyConstant.buyMap.remove(task.getDigitalCollectionGroupId(),task.getMaxSinglePrice());
        MarketCollectionEntity marketCollectionEntity = marketCollectionService.getById(task.getDigitalCollectionGroupId());
        BuyConstant.picMap.remove(marketCollectionEntity.getCoverPicUrl(),task.getDigitalCollectionGroupId());

        purchaseTaskService.updateById(update);

        res.setSuccess(stopped);
        res.setRunning(false);
        res.setMessage(stopped ? "任务已停止" : "任务未在运行");
        res.setEndTime(LocalDateTime.now());
        return res;
    }

    private void executeTaskLoop(Long taskId) {
        PurchaseTaskEntity task = purchaseTaskService.getById(taskId);
        if (task != null && normalizeTaskType(task.getTaskType()) == TASK_TYPE_FAST_PURCHASE) {
            executeFastPurchaseTaskLoop(taskId);
            return;
        }
        executeBatchPurchaseTaskLoop(taskId);
    }

    /**
     * 每轮需要拿到“账号数 * 10”次有效请求。
     * 只有真正拿到 iBox 业务响应的请求才计入分钟额度，代理超时、异常、null 响应都不计数。
     */
    private void executeBatchPurchaseTaskLoop(Long taskId) {
        if (shouldStop(taskId)) {
            return;
        }

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
            // 每次循环都检查停止状态，保证点击停止后能尽快退出，而不是继续跑完整轮。
            if (shouldStop(taskId)) {
                return;
            }
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
                    feishuMessageUtil.sendText("iBox批量购买任务成功，任务名称：" + task.getTaskName() + "，商品名称：" + task.getCollectionName() + "，商品ID：" + task.getDigitalCollectionGroupId() + "，账号ID：" + account.getId() + "，订单ID：" + purchaseResult.orderId);
                    if (isTargetReached(task.getMaxCount(), purchasedCount)) {
                        stopTask(taskId);
                        return;
                    }
                }
            } catch (Exception e) {
                log.warn("【批量购买任务】请求处理异常，taskId:{}, accountId:{}, collectionId:{}", taskId, account.getId(), task.getDigitalCollectionGroupId(), e);
            }

            if (validRequestCount < targetValidRequestCount) {
                sleep(intervalMillis);
            }
        }

        long activeMillis = System.currentTimeMillis() - cycleStartMillis;
        long restMillis = Math.max(60000L - activeMillis, 0L);
        if (restMillis > 0) {
            log.info("【批量购买任务】本轮有效请求完成，taskId:{}, validRequestCount:{}, activeMillis:{}, restMillis:{}", taskId, validRequestCount, activeMillis, restMillis);
            sleep(restMillis);
        }
    }

    /**
     * 快速购买任务循环。
     */
    private void executeFastPurchaseTaskLoop(Long taskId) {
        if (shouldStop(taskId)) {
            return;
        }

        PurchaseTaskEntity task = purchaseTaskService.getById(taskId);
        if (task == null) {
            taskAccountIndexMap.remove(taskId);
            taskFastScanIndexMap.remove(taskId);
            taskScanWindowMap.remove(taskId);
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
            log.warn("【快速购买任务】未配置可用账号，taskId:{}", taskId);
            sleep(1000L);
            return;
        }

        long intervalMillis = normalizeInterval(task.getIntervalMillis());
        IboxAccountEntity purchaseAccount = getAccount(accountIds.get(0));
        if (purchaseAccount == null || !StringUtils.hasText(purchaseAccount.getBearerToken())) {
            log.warn("【快速购买任务】首个购买账号不可用，taskId:{}, accountId:{}", taskId, accountIds.get(0));
            sleep(intervalMillis);
            return;
        }

        ScanAccountSelection selection = getNextFastScanAccount(taskId, accountIds);
        if (selection == null || selection.account() == null) {
            long restMillis = getFastScanRestMillis(taskId, accountIds);
            sleep(restMillis > 0 ? restMillis : intervalMillis);
            return;
        }
        IboxAccountEntity scanAccount = selection.account();
        if (!StringUtils.hasText(scanAccount.getBearerToken())) {
            log.warn("【快速购买任务】扫描账号Token为空，跳过本次扫描，taskId:{}, accountId:{}", taskId, scanAccount.getId());
            sleep(intervalMillis);
            return;
        }

        try {
            IboxApiResponse<ConsignmentOrderListRes> listResponse = fetchConsignmentOrderList(task, scanAccount);
            if (listResponse == null || listResponse.getCode() == null || listResponse.getCode() != 0 || listResponse.getData() == null) {
                log.warn("【快速购买任务】在售列表响应无效，taskId:{}, accountId:{}, response:{}", taskId, scanAccount.getId(), JSONObject.toJSONString(listResponse));
                sleep(intervalMillis);
                return;
            }
            recordFastScan(taskId, selection.accountId());

            ConsignmentOrderRes matchedOrder = pickPurchasableOrder(task, listResponse.getData());
            if (matchedOrder == null) {
                log.info("【快速购买任务】未找到可购买订单，taskId:{}, accountId:{}", taskId, scanAccount.getId());
                sleep(intervalMillis);
                return;
            }

            log.info("【快速购买任务】命中可购买订单，scanAccountId:{}, purchaseAccountId:{}, digitalCollectionId:{}, lockType:{}", scanAccount.getId(), purchaseAccount.getId(), matchedOrder.getDigitalCollectionId(), matchedOrder.getDigitalCollection() == null ? null : matchedOrder.getDigitalCollection().getLockType());
            PurchaseResult purchaseResult = doFastPurchase(task, purchaseAccount, matchedOrder);
            if (purchaseResult.success) {
                int purchasedCount = task.getPurchasedCount() == null ? 0 : task.getPurchasedCount();
                purchasedCount++;
                updatePurchasedCount(taskId, purchasedCount);
                feishuMessageUtil.sendText("iBox快速购买任务成功，任务名称：" + task.getTaskName() + "，藏品名称：" + safeCollectionName(matchedOrder) + "，藏品ID：" + matchedOrder.getDigitalCollectionId() + "，账号ID：" + purchaseAccount.getId() + "，订单ID：" + purchaseResult.orderId);
                if (isTargetReached(task.getMaxCount(), purchasedCount)) {
                    stopTask(taskId);
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("【快速购买任务】请求处理异常，taskId:{}, scanAccountId:{}, purchaseAccountId:{}", taskId, scanAccount.getId(), purchaseAccount.getId(), e);
        }

        sleep(intervalMillis);
    }

    /**
     * 执行一次批量购买请求。
     */
    private PurchaseResult doPurchase(PurchaseTaskEntity task, IboxAccountEntity account) {
        BatchPurchaseConsignmentOrdersReq req = buildBatchPurchaseRequest(task);
        IboxApiResponse<Map<String, Object>> purchaseResponse;
        ProxyInfo nextConfigProxy = iboxProxyService.getNextConfigProxy();
        try {
            purchaseResponse = iboxClient.post(account.getBearerToken(), String.format(UrlConstant.BATCH_PURCHASE_CONSIGNMENT_ORDER_URL, account.getIboxUid()), req, new TypeReference<>() {
            }, nextConfigProxy);
        } catch (Exception e) {
            log.warn("【批量购买任务】请求异常，不计入有效请求，taskId:{}, accountId:{}, collectionId:{}", task.getId(), account.getId(), task.getDigitalCollectionGroupId(), e);
            return PurchaseResult.invalid();
        }

        log.info("【批量购买任务】响应结果，taskId:{}, accountId:{}, result:{}", task.getId(), account.getId(), JSONObject.toJSONString(purchaseResponse));
        if (purchaseResponse == null || purchaseResponse.getCode() == null) {
            log.warn("【批量购买任务】响应为空或缺少业务码，不计入有效请求，taskId:{}, accountId:{}", task.getId(), account.getId());
            return PurchaseResult.invalid();
        }
        if (!isPurchaseSuccess(purchaseResponse)) {
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
        return purchaseResponse != null && purchaseResponse.getCode() != null && purchaseResponse.getCode() == 0 && purchaseResponse.getData() != null && purchaseResponse.getData().get("orderId") != null;
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
            IboxApiResponse<OrderPaymentInfoRes> paymentInfoResponse = iboxClient.get(account.getBearerToken(), String.format(UrlConstant.ORDER_PAYMENT_INFO_URL, orderId, 0), null, OrderPaymentInfoRes.class);
            if (paymentInfoResponse == null || paymentInfoResponse.getCode() == null || paymentInfoResponse.getCode() != 0 || paymentInfoResponse.getData() == null) {
                log.warn("【批量购买任务】获取订单支付信息失败，orderId:{}, response:{}", orderId, JSONObject.toJSONString(paymentInfoResponse));
                return;
            }
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
     * 快速购买任务扫描在售列表。
     */
    private IboxApiResponse<ConsignmentOrderListRes> fetchConsignmentOrderList(PurchaseTaskEntity task, IboxAccountEntity account) {
        ProxyInfo nextConfigProxy = iboxProxyService.getNextConfigProxy();
        String url = String.format(UrlConstant.CONSIGNMENT_ORDER_LIST_URL, task.getDigitalCollectionGroupId(), 1, FAST_SCAN_PAGE_SIZE, 1, 1, account.getIboxUid());
        return iboxClient.get(account.getBearerToken(), url, null, new TypeReference<>() {
        }, nextConfigProxy);
    }

    /**
     * 从在售列表中优先挑选 lockType == 2 的可购买藏品。
     */
    private ConsignmentOrderRes pickPurchasableOrder(PurchaseTaskEntity task, ConsignmentOrderListRes listRes) {
        if (listRes == null || CollectionUtils.isEmpty(listRes.getList())) {
            return null;
        }
        int count = 0;
        for (ConsignmentOrderRes order : listRes.getList()) {
            if (count >= 10) {
                break;
            }
            if (order == null || order.getDigitalCollection() == null) {
                continue;
            }
            log.info("【快速购买】collectionName:{}, price:{}, lockType:{}", order.getDigitalCollection().getName(), order.getPrice(), order.getDigitalCollection().getLockType());
            if (Integer.valueOf(2).equals(order.getDigitalCollection().getLockType()) && order.getPrice().compareTo(task.getMaxSinglePrice()) <= 0) {
                return order;
            }
            count++;
        }
        return null;
    }

    /**
     * 快速购买：始终使用首个账号下单。
     */
    private PurchaseResult doFastPurchase(PurchaseTaskEntity task, IboxAccountEntity account, ConsignmentOrderRes order) {
        PurchaseConsignmentOrdersReq req = new PurchaseConsignmentOrdersReq();
        req.setDigitalCollectionId(order.getDigitalCollectionId());
        req.setPaymentPlatformCode(task.getPaymentPlatformCode() == null ? 30L : task.getPaymentPlatformCode().longValue());
        ProxyInfo nextConfigProxy = iboxProxyService.getNextConfigProxy();
        IboxApiResponse<Map<String, Object>> purchaseResponse;
        try {
            purchaseResponse = iboxClient.post(account.getBearerToken(), UrlConstant.PURCHASE_CONSIGNMENT_ORDER_URL, req, new TypeReference<>() {
            }, nextConfigProxy);
        } catch (Exception e) {
            log.warn("【快速购买任务】下单异常，taskId:{}, accountId:{}, digitalCollectionId:{}", task.getId(), account.getId(), order.getDigitalCollectionId(), e);
            return PurchaseResult.invalid();
        }

        log.info("【快速购买任务】下单响应，taskId:{}, accountId:{}, result:{}", task.getId(), account.getId(), JSONObject.toJSONString(purchaseResponse));
        if (purchaseResponse == null || purchaseResponse.getCode() == null) {
            return PurchaseResult.invalid();
        }
        if (!isPurchaseSuccess(purchaseResponse)) {
            return PurchaseResult.validFail();
        }

        String orderId = String.valueOf(purchaseResponse.getData().get("orderId"));
        saveFastSuccessOrder(task, account, req, purchaseResponse, orderId, order);
        logOrderPaymentLink(account, orderId);
        return PurchaseResult.success(orderId);
    }

    /**
     * 保存快速购买成功的订单记录。
     */
    private void saveFastSuccessOrder(PurchaseTaskEntity task, IboxAccountEntity account, PurchaseConsignmentOrdersReq req, IboxApiResponse<Map<String, Object>> purchaseResponse, String orderId, ConsignmentOrderRes order) {
        PurchaseOrderEntity purchaseOrder = new PurchaseOrderEntity();
        purchaseOrder.setTaskId(task.getId());
        purchaseOrder.setAccountId(account.getId());
        purchaseOrder.setDigitalCollectionId(order.getDigitalCollectionId());
        purchaseOrder.setRequestType(1);
        purchaseOrder.setRequestPayload(JSONObject.toJSONString(req));
        purchaseOrder.setResponsePayload(JSONObject.toJSONString(purchaseResponse));
        purchaseOrder.setIboxOrderId(orderId);
        purchaseOrder.setStatus(1);
        purchaseOrder.setSubmittedAt(LocalDateTime.now());
        purchaseOrder.setFinishedAt(LocalDateTime.now());
        purchaseOrderService.save(purchaseOrder);
    }

    /**
     * 快速购买任务选择下一个扫描账号。
     */
    private ScanAccountSelection getNextFastScanAccount(Long taskId, List<Long> accountIds) {
        if (CollectionUtils.isEmpty(accountIds)) {
            return null;
        }
        Map<Long, AccountScanWindow> windowMap = taskScanWindowMap.computeIfAbsent(taskId, key -> new ConcurrentHashMap<>());
        cleanupExpiredWindows(windowMap);

        AtomicInteger index = taskFastScanIndexMap.computeIfAbsent(taskId, key -> new AtomicInteger(0));
        int startIndex = Math.floorMod(index.getAndIncrement(), accountIds.size());
        long now = System.currentTimeMillis();
        for (int offset = 0; offset < accountIds.size(); offset++) {
            Long accountId = accountIds.get((startIndex + offset) % accountIds.size());
            AccountScanWindow window = windowMap.computeIfAbsent(accountId, key -> new AccountScanWindow());
            if (window.canScan(now, FAST_SCAN_REQUESTS_PER_ACCOUNT_PER_MINUTE)) {
                IboxAccountEntity account = getAccount(accountId);
                if (account != null) {
                    return new ScanAccountSelection(accountId, account);
                }
            }
        }
        return null;
    }

    /**
     * 快速购买在拿到有效业务响应后，才记录本次扫描额度。
     */
    private void recordFastScan(Long taskId, Long accountId) {
        if (taskId == null || accountId == null) {
            return;
        }
        Map<Long, AccountScanWindow> windowMap = taskScanWindowMap.computeIfAbsent(taskId, key -> new ConcurrentHashMap<>());
        AccountScanWindow window = windowMap.computeIfAbsent(accountId, key -> new AccountScanWindow());
        window.record(System.currentTimeMillis());
    }

    /**
     * 快速购买账号限频时，计算距离下一次可扫描还要等待多久。
     */
    private long getFastScanRestMillis(Long taskId, List<Long> accountIds) {
        Map<Long, AccountScanWindow> windowMap = taskScanWindowMap.get(taskId);
        if (windowMap == null || CollectionUtils.isEmpty(accountIds)) {
            return 1000L;
        }
        long now = System.currentTimeMillis();
        long rest = Long.MAX_VALUE;
        for (Long accountId : accountIds) {
            AccountScanWindow window = windowMap.get(accountId);
            if (window == null) {
                return 0L;
            }
            rest = Math.min(rest, window.getRestMillis(now, FAST_SCAN_REQUESTS_PER_ACCOUNT_PER_MINUTE));
        }
        return rest == Long.MAX_VALUE ? 1000L : Math.max(rest, 0L);
    }

    /**
     * 清理过期的扫描窗口。
     */
    private void cleanupExpiredWindows(Map<Long, AccountScanWindow> windowMap) {
        if (windowMap == null || windowMap.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        windowMap.values().removeIf(window -> window.isExpired(now));
    }

    /**
     * 兼容任务类型默认值。
     */
    private int normalizeTaskType(Integer taskType) {
        return taskType == null ? TASK_TYPE_BATCH_PURCHASE : taskType;
    }

    /**
     * 安全获取藏品名称。
     */
    private String safeCollectionName(ConsignmentOrderRes order) {
        if (order == null) {
            return "-";
        }
        if (StringUtils.hasText(order.getDigitalCollection().getName())) {
            return order.getDigitalCollection().getName();
        }
        return order.getDigitalCollection() == null || !StringUtils.hasText(order.getDigitalCollection().getName()) ? "-" : order.getDigitalCollection().getName();
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
     * 判断当前任务是否应立即停止。
     * 点击停止后，运行态会先从运行管理器移除，这里要及时感知并退出热循环。
     */
    private boolean shouldStop(Long taskId) {
        return Thread.currentThread().isInterrupted() || !purchaseTaskRuntimeManager.isRunning(taskId);
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
        return accountIds.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), values -> String.join(",", values)));
    }

    /**
     * 将逗号分隔的账号ID字符串解析为列表，保留原有顺序。
     */
    private List<Long> parseAccountIds(String accountIds) {
        if (!StringUtils.hasText(accountIds)) {
            return new ArrayList<>();
        }
        return Arrays.stream(accountIds.split(",")).map(String::trim).filter(StringUtils::hasText).distinct().map(value -> {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException e) {
                log.warn("【批量购买任务】解析账号ID失败，value:{}", value);
                return null;
            }
        }).filter(Objects::nonNull).toList();
    }

    /**
     * 将账号ID列表转换为账号名称列表，便于前端直接展示。
     */
    private List<String> buildAccountNames(List<Long> accountIds) {
        if (CollectionUtils.isEmpty(accountIds)) {
            return new ArrayList<>();
        }
        return accountIds.stream().map(this::getAccount).filter(Objects::nonNull).map(account -> StringUtils.hasText(account.getPhone()) ? account.getAccountName() + "(" + account.getPhone() + ")" : account.getAccountName() + "(ID:" + account.getId() + ")").toList();
    }

    /**
     * 统一处理休眠。线程一旦被中断，立刻返回，让外层尽快结束任务。
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

    /**
     * 快速购买扫描窗口。
     */
    private static class AccountScanWindow {
        private final Deque<Long> timestamps = new ArrayDeque<>();

        private boolean canScan(long now, int limitPerMinute) {
            trim(now);
            return timestamps.size() < limitPerMinute;
        }

        private void record(long now) {
            timestamps.addLast(now);
            trim(now);
        }

        private long getRestMillis(long now, int limitPerMinute) {
            trim(now);
            if (timestamps.size() < limitPerMinute || timestamps.isEmpty()) {
                return 0L;
            }
            return Math.max(60000L - (now - timestamps.peekFirst()), 0L);
        }

        private boolean isExpired(long now) {
            trim(now);
            return timestamps.isEmpty();
        }

        private void trim(long now) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= 60000L) {
                timestamps.removeFirst();
            }
        }
    }

    /**
     * 快速购买扫描账号选择结果。
     */
    private record ScanAccountSelection(Long accountId, IboxAccountEntity account) {
    }
}
