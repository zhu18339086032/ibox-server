package com.ibox.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.PurchaseTaskRes;
import com.ibox.service.IboxAccountService;
import com.ibox.service.IboxProxyService;
import com.ibox.service.PurchaseOrderService;
import com.ibox.service.PurchaseTaskService;
import com.ibox.utils.FeishuMessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 批量购买任务服务实现类
 */
@Slf4j
@Service
public class PurchaseTaskManagerImpl implements PurchaseTaskManager {

    /**
     * 单次批量购买请求固定只尝试购买 1 个，
     * 任务总购买数由 PurchaseTaskEntity.maxCount 控制。
     */
    private static final int SINGLE_REQUEST_MAX_COUNT = 1;

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
        queryWrapper.eq(req.getAccountId() != null, PurchaseTaskEntity::getAccountId, req.getAccountId());
        queryWrapper.eq(req.getStatus() != null, PurchaseTaskEntity::getStatus, req.getStatus());
        queryWrapper.eq(req.getRunning() != null, PurchaseTaskEntity::getRunning, req.getRunning());
        queryWrapper.orderByDesc(PurchaseTaskEntity::getId);
        IPage<PurchaseTaskEntity> page = purchaseTaskService.page(new Page<>(req.getPageNum(), req.getPageSize()), queryWrapper);
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::buildRes).toList());
    }

    @Override
    public PurchaseTaskRes getById(Long id) {
        PurchaseTaskEntity entity = purchaseTaskService.getById(id);
        if (entity == null) {
            return null;
        }
        return buildRes(entity);
    }

    @Override
    public boolean save(PurchaseTaskSaveReq req) {
        PurchaseTaskEntity entity = new PurchaseTaskEntity();
        BeanUtils.copyProperties(req, entity);
        entity.setTaskType(2);
        entity.setRunning(0);
        entity.setPurchasedCount(0);
        return purchaseTaskService.save(entity);
    }

    @Override
    public boolean updateById(PurchaseTaskUpdateReq req) {
        PurchaseTaskEntity entity = new PurchaseTaskEntity();
        BeanUtils.copyProperties(req, entity);
        return purchaseTaskService.updateById(entity);
    }

    @Override
    public boolean removeById(Long id) {
        purchaseTaskRuntimeManager.stop(id);
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

        if (isTargetReached(task)) {
            res.setSuccess(false);
            res.setRunning(false);
            res.setMessage("批量购买任务已达到目标购买数");
            res.setEndTime(LocalDateTime.now());
            return res;
        }

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

    private void executeTaskLoop(Long taskId) {
        // 每轮只查询一次任务配置，既能感知任务最新状态，也避免成功路径重复查库。
        PurchaseTaskEntity task = purchaseTaskService.getById(taskId);
        if (task == null) {
            purchaseTaskRuntimeManager.stop(taskId);
            return;
        }
        // 任务被停用后不再继续执行购买。
        if (task.getStatus() == null || task.getStatus() != 1) {
            stopTask(taskId);
            return;
        }
        // 已购买数达到目标数后自动停任务。
        if (isTargetReached(task)) {
            stopTask(taskId);
            return;
        }

        IboxAccountEntity account = getAccount(task.getAccountId());
        if (account == null || account.getBearerToken() == null || account.getBearerToken().isBlank()) {
            sleep(task.getIntervalMillis());
            return;
        }

        BatchPurchaseConsignmentOrdersReq req = buildBatchPurchaseRequest(task);
        IboxApiResponse<Map<String, Object>> purchaseResponse;
        try {
            purchaseResponse = iboxClient.post(
                    account.getBearerToken(),
                    String.format(UrlConstant.BATCH_PURCHASE_CONSIGNMENT_ORDER_URL, account.getIboxUid()),
                    req,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    }
            );
        } catch (Exception e) {
            log.warn("【批量购买任务】请求异常, taskId:{}, collectionId:{}", taskId, task.getDigitalCollectionGroupId(), e);
            sleep(task.getIntervalMillis());
            return;
        }

        log.info("【批量购买任务】响应结果: {}", JSONObject.toJSONString(purchaseResponse));
        if (isPurchaseSuccess(purchaseResponse)) {
            String orderId = String.valueOf(purchaseResponse.getData().get("orderId"));
            // 只有真正成功并拿到订单号时才保存订单记录。
            saveSuccessOrder(task, req, purchaseResponse, orderId);
            // 使用当前任务快照直接累加已购买数，避免成功后再次查询任务表。
            int nextPurchasedCount = (task.getPurchasedCount() == null ? 0 : task.getPurchasedCount()) + SINGLE_REQUEST_MAX_COUNT;
            boolean reachedTarget = isTargetReached(task.getMaxCount(), nextPurchasedCount);

            if (reachedTarget) {
                PurchaseTaskEntity update = new PurchaseTaskEntity();
                update.setId(taskId);
                update.setPurchasedCount(nextPurchasedCount);
                update.setRunning(0);
                purchaseTaskService.updateById(update);
                purchaseTaskRuntimeManager.stop(taskId);
            } else {
                PurchaseTaskEntity update = new PurchaseTaskEntity();
                update.setId(taskId);
                update.setPurchasedCount(nextPurchasedCount);
                purchaseTaskService.updateById(update);
            }

            feishuMessageUtil.sendText("iBox批量购买任务成功，任务名称：" + task.getTaskName()
                    + "，商品名称：" + task.getCollectionName()
                    + "，商品ID：" + task.getDigitalCollectionGroupId()
                    + "，订单ID：" + orderId);
        }

        sleep(task.getIntervalMillis());
    }

    /**
     * 构建单次批量购买请求。
     * 这里固定单次只买 1 个，避免把任务总目标数直接透传给接口。
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
     * 购买成功必须同时满足：
     * 1. 接口返回 code == 0
     * 2. 返回体里存在有效 orderId
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
    private void saveSuccessOrder(PurchaseTaskEntity task,
                                  BatchPurchaseConsignmentOrdersReq req,
                                  IboxApiResponse<Map<String, Object>> purchaseResponse,
                                  String orderId) {
        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setTaskId(task.getId());
        order.setAccountId(task.getAccountId());
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
     * 累加任务已购买数。
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
     * 将任务运行状态置为停止，并终止后台循环。
     */
    private void stopTask(Long taskId) {
        PurchaseTaskEntity update = new PurchaseTaskEntity();
        update.setId(taskId);
        update.setRunning(0);
        purchaseTaskService.updateById(update);
        purchaseTaskRuntimeManager.stop(taskId);
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

    private void sleep(Long intervalMillis) {
        try {
            Thread.sleep(intervalMillis == null || intervalMillis <= 0 ? 100L : intervalMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private PurchaseTaskRes buildRes(PurchaseTaskEntity entity) {
        PurchaseTaskRes res = new PurchaseTaskRes();
        BeanUtils.copyProperties(entity, res);
        return res;
    }
}
