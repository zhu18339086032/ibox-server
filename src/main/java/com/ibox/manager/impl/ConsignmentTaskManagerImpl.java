package com.ibox.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.common.ConsignmentTaskRuntimeManager;
import com.ibox.common.IboxAccountCache;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.ConsignmentTaskEntity;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.manager.ConsignmentTaskManager;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.UserDigitalCollectionGroupPageRes;
import com.ibox.model.res.ibox.UserDigitalCollectionItemRes;
import com.ibox.model.req.web.ConsignmentTaskPageReq;
import com.ibox.model.req.web.ConsignmentTaskSaveReq;
import com.ibox.model.req.web.ConsignmentTaskUpdateReq;
import com.ibox.model.res.web.ConsignmentTaskRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.service.ConsignmentTaskService;
import com.ibox.service.IboxAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 寄售任务服务实现
 */
@Slf4j
@Service
public class ConsignmentTaskManagerImpl implements ConsignmentTaskManager {

    /**
     * 上架任务类型
     */
    private static final String TASK_TYPE_LIST = "LIST";

    /**
     * 下架任务类型
     */
    private static final String TASK_TYPE_DELIST = "DELIST";

    /**
     * 任务状态：禁用
     */
    private static final int STATUS_DISABLED = 0;

    /**
     * 任务状态：启用
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 获取持有商品列表接口
     */
    private static final String USER_DIGITAL_COLLECTION_GROUP_URL =
            UrlConstant.SAIL_API_BASE_URL + "/personal-center-service/users/digital-collection-groups/%s?pageNo=%s&pageSize=%s";

    /**
     * 创建寄售订单接口
     */
    private static final String CREATE_CONSIGNMENT_ORDER_URL =
            UrlConstant.SAIL_API_BASE_URL + "/order-create-service/consignment-orders";

    /**
     * 下架寄售订单接口
     */
    private static final String CANCEL_CONSIGNMENT_ORDER_URL =
            UrlConstant.SAIL_API_BASE_URL + "/order-service/consign-orders/%s/cancel";

    /**
     * 固定分页大小
     */
    private static final int PAGE_SIZE = 20;

    @Autowired
    private ConsignmentTaskService consignmentTaskService;

    @Autowired
    private IboxAccountService iboxAccountService;

    @Autowired
    private IboxAccountCache iboxAccountCache;

    @Autowired
    private IboxClient iboxClient;

    @Autowired
    private ConsignmentTaskRuntimeManager consignmentTaskRuntimeManager;

    /**
     * 寄售密码统一从自动支付配置中读取，避免每个任务单独维护密码。
     */
    @Value("${ibox.auto-pay.password:}")
    private String autoPayPassword;

    @Override
    public PageResponse<ConsignmentTaskRes> list(ConsignmentTaskPageReq req) {
        LambdaQueryWrapper<ConsignmentTaskEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(req.getTaskName()), ConsignmentTaskEntity::getTaskName, req.getTaskName());
        queryWrapper.eq(req.getAccountId() != null, ConsignmentTaskEntity::getAccountId, req.getAccountId());
        queryWrapper.eq(StringUtils.hasText(req.getTaskType()), ConsignmentTaskEntity::getTaskType, normalizeTaskType(req.getTaskType()));
        queryWrapper.eq(req.getStatus() != null, ConsignmentTaskEntity::getStatus, req.getStatus());
        queryWrapper.eq(req.getRunning() != null, ConsignmentTaskEntity::getRunning, req.getRunning());
        queryWrapper.orderByDesc(ConsignmentTaskEntity::getId);
        IPage<ConsignmentTaskEntity> page = consignmentTaskService.page(new Page<>(req.getPageNum(), req.getPageSize()), queryWrapper);
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::buildRes).toList());
    }

    @Override
    public ConsignmentTaskRes getById(Long id) {
        ConsignmentTaskEntity entity = consignmentTaskService.getById(id);
        return entity == null ? null : buildRes(entity);
    }

    @Override
    public boolean save(ConsignmentTaskSaveReq req) {
        validateTaskReq(req);
        ConsignmentTaskEntity entity = new ConsignmentTaskEntity();
        BeanUtils.copyProperties(req, entity);
        entity.setTaskType(normalizeTaskType(req.getTaskType()));
        entity.setTargetCount(normalizeTargetCount(req.getTargetCount()));
        entity.setIntervalMillis(normalizeInterval(req.getIntervalMillis()));
        entity.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        entity.setRunning(0);
        entity.setExecutedCount(0);
        entity.setSuccessCount(0);
        entity.setFailCount(0);
        entity.setLastMessage("未执行");
        if (TASK_TYPE_DELIST.equals(entity.getTaskType())) {
            entity.setConsignPrice(null);
        }
        return consignmentTaskService.save(entity);
    }

    @Override
    public boolean updateById(ConsignmentTaskUpdateReq req) {
        validateTaskReq(req);
        ConsignmentTaskEntity entity = new ConsignmentTaskEntity();
        entity.setId(req.getId());
        entity.setTaskName(req.getTaskName());
        entity.setAccountId(req.getAccountId());
        entity.setDigitalCollectionGroupId(req.getDigitalCollectionGroupId());
        entity.setCollectionName(req.getCollectionName());
        entity.setTaskType(normalizeTaskType(req.getTaskType()));
        entity.setTargetCount(normalizeTargetCount(req.getTargetCount()));
        entity.setIntervalMillis(normalizeInterval(req.getIntervalMillis()));
        entity.setConsignPrice(TASK_TYPE_DELIST.equals(normalizeTaskType(req.getTaskType())) ? null : req.getConsignPrice());
        entity.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        entity.setRemark(req.getRemark());
        return consignmentTaskService.updateById(entity);
    }

    @Override
    public boolean removeById(Long id) {
        consignmentTaskRuntimeManager.stop(id);
        ConsignmentTaskEntity update = new ConsignmentTaskEntity();
        update.setId(id);
        update.setRunning(0);
        consignmentTaskService.updateById(update);
        return consignmentTaskService.removeById(id);
    }

    @Override
    public MonitorTaskExecuteRes startById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        ConsignmentTaskEntity task = consignmentTaskService.getById(id);
        if (task == null) {
            return buildExecuteRes(res, false, false, "任务不存在");
        }
        if (task.getStatus() == null || task.getStatus() != STATUS_ENABLED) {
            return buildExecuteRes(res, false, false, "任务未启用");
        }
        IboxAccountEntity account = getAccount(task.getAccountId());
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            return buildExecuteRes(res, false, false, "账号不存在或token为空");
        }
        if (!StringUtils.hasText(autoPayPassword)) {
            return buildExecuteRes(res, false, false, "未配置寄售密码");
        }

        boolean started = consignmentTaskRuntimeManager.start(id, () -> executeTaskLoop(id));
        if (!started) {
            return buildExecuteRes(res, false, true, "任务已在运行中");
        }

        ConsignmentTaskEntity update = new ConsignmentTaskEntity();
        update.setId(id);
        update.setRunning(1);
        update.setLastMessage("任务已启动");
        consignmentTaskService.updateById(update);
        return buildExecuteRes(res, true, true, "任务已启动");
    }

    @Override
    public MonitorTaskExecuteRes stopById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        boolean stopped = consignmentTaskRuntimeManager.stop(id);
        ConsignmentTaskEntity update = new ConsignmentTaskEntity();
        update.setId(id);
        update.setRunning(0);
        update.setLastMessage(stopped ? "任务已停止" : "任务未运行");
        consignmentTaskService.updateById(update);
        return buildExecuteRes(res, stopped, false, stopped ? "任务已停止" : "任务未运行");
    }

    /**
     * 执行寄售任务主循环
     */
    private void executeTaskLoop(Long taskId) {
        if (shouldStop(taskId)) {
            return;
        }

        ConsignmentTaskEntity task = consignmentTaskService.getById(taskId);
        if (task == null) {
            stopTask(taskId, "任务不存在");
            return;
        }
        if (task.getStatus() == null || task.getStatus() != STATUS_ENABLED) {
            stopTask(taskId, "任务未启用");
            return;
        }

        IboxAccountEntity account = getAccount(task.getAccountId());
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            stopTask(taskId, "账号不存在或token为空");
            return;
        }

        List<UserDigitalCollectionItemRes> allItems = fetchAllCollections(account.getBearerToken(), task.getDigitalCollectionGroupId());
        if (CollectionUtils.isEmpty(allItems)) {
            stopTask(taskId, "未获取到可处理商品");
            return;
        }

        List<UserDigitalCollectionItemRes> candidates = filterCandidates(task, allItems);
        if (CollectionUtils.isEmpty(candidates)) {
            stopTask(taskId, TASK_TYPE_LIST.equals(task.getTaskType()) ? "没有可上架商品" : "没有可下架商品");
            return;
        }

        int targetCount = Math.min(normalizeTargetCount(task.getTargetCount()), candidates.size());
        long intervalMillis = normalizeInterval(task.getIntervalMillis());
        int successCount = task.getSuccessCount() == null ? 0 : task.getSuccessCount();
        int failCount = task.getFailCount() == null ? 0 : task.getFailCount();
        int executedCount = task.getExecutedCount() == null ? 0 : task.getExecutedCount();

        for (int index = 0; index < targetCount; index++) {
            if (shouldStop(taskId)) {
                return;
            }

            UserDigitalCollectionItemRes item = candidates.get(index);
            ConsignmentActionResult result = TASK_TYPE_LIST.equals(task.getTaskType())
                    ? createConsignmentOrder(account.getBearerToken(), item, task)
                    : cancelConsignmentOrder(account.getBearerToken(), item);

            executedCount++;
            if (result.success()) {
                successCount++;
            } else {
                failCount++;
            }

            updateTaskProgress(taskId, executedCount, successCount, failCount, result.message());
            if (index < targetCount - 1) {
                sleep(intervalMillis);
            }
        }

        stopTask(taskId, "任务执行完成");
    }

    /**
     * 分页拉取指定商品组下的全部持有商品
     */
    private List<UserDigitalCollectionItemRes> fetchAllCollections(String token, Long groupId) {
        List<UserDigitalCollectionItemRes> result = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            String url = String.format(USER_DIGITAL_COLLECTION_GROUP_URL, groupId, pageNo, PAGE_SIZE);
            IboxApiResponse<UserDigitalCollectionGroupPageRes> response = iboxClient.get(
                    token,
                    url,
                    null,
                    new TypeReference<UserDigitalCollectionGroupPageRes>() {
                    }
            );
            if (response == null || response.getCode() == null || response.getCode() != 0 || response.getData() == null) {
                log.warn("【寄售任务】获取持有商品列表失败，groupId:{}, pageNo:{}, response:{}", groupId, pageNo, JSONObject.toJSONString(response));
                break;
            }

            if (!CollectionUtils.isEmpty(response.getData().getList())) {
                result.addAll(response.getData().getList());
            }
            if (!Boolean.TRUE.equals(response.getData().getHasMore())) {
                break;
            }
            pageNo++;
            sleep(300L);
        }
        return result;
    }

    /**
     * 按任务类型过滤候选商品
     */
    private List<UserDigitalCollectionItemRes> filterCandidates(ConsignmentTaskEntity task, List<UserDigitalCollectionItemRes> allItems) {
        if (CollectionUtils.isEmpty(allItems)) {
            return Collections.emptyList();
        }
        return allItems.stream()
                .filter(Objects::nonNull)
                .filter(item -> TASK_TYPE_LIST.equals(task.getTaskType())
                        ? Integer.valueOf(0).equals(item.getDigitalCollectionStatus())
                        : Integer.valueOf(2).equals(item.getDigitalCollectionStatus()))
                .toList();
    }

    /**
     * 调用上架接口创建寄售订单
     */
    private ConsignmentActionResult createConsignmentOrder(String token, UserDigitalCollectionItemRes item, ConsignmentTaskEntity task) {
        CreateConsignmentOrderReq req = new CreateConsignmentOrderReq();
        req.setDigitalCollectionId(item.getId());
        req.setPrice(task.getConsignPrice());
        req.setPaymentPlatformCodes(List.of(30));
        req.setConsignPassword(autoPayPassword);

        IboxApiResponse<CreateConsignmentOrderRes> response = iboxClient.post(
                token,
                CREATE_CONSIGNMENT_ORDER_URL,
                req,
                new TypeReference<CreateConsignmentOrderRes>() {
                }
        );
        if (response == null) {
            return ConsignmentActionResult.fail("上架失败，response为空，商品ID:" + item.getId());
        }
        if (response.getCode() != null && response.getCode() == 0 && response.getData() != null) {
            return ConsignmentActionResult.success("上架成功，商品ID:" + item.getId() + "，订单ID:" + response.getData().getOrderId());
        }
        return ConsignmentActionResult.fail("上架失败，商品ID:" + item.getId() + "，response:" + JSONObject.toJSONString(response));
    }

    /**
     * 调用下架接口取消寄售订单
     */
    private ConsignmentActionResult cancelConsignmentOrder(String token, UserDigitalCollectionItemRes item) {
        if (item.getOrderId() == null) {
            return ConsignmentActionResult.fail("下架失败，orderId为空，商品ID:" + item.getId());
        }
        String url = String.format(CANCEL_CONSIGNMENT_ORDER_URL, item.getOrderId());
        IboxApiResponse<CancelConsignmentOrderRes> response = iboxClient.post(
                token,
                url,
                Collections.emptyMap(),
                new TypeReference<CancelConsignmentOrderRes>() {
                }
        );
        if (response == null) {
            return ConsignmentActionResult.fail("下架失败，response为空，商品ID:" + item.getId() + "，orderId:" + item.getOrderId());
        }
        if (response.getCode() != null && response.getCode() == 0) {
            return ConsignmentActionResult.success("下架成功，商品ID:" + item.getId() + "，orderId:" + item.getOrderId());
        }
        return ConsignmentActionResult.fail("下架失败，商品ID:" + item.getId() + "，response:" + JSONObject.toJSONString(response));
    }

    /**
     * 更新任务执行进度
     */
    private void updateTaskProgress(Long taskId, int executedCount, int successCount, int failCount, String lastMessage) {
        ConsignmentTaskEntity update = new ConsignmentTaskEntity();
        update.setId(taskId);
        update.setExecutedCount(executedCount);
        update.setSuccessCount(successCount);
        update.setFailCount(failCount);
        update.setLastMessage(lastMessage);
        consignmentTaskService.updateById(update);
    }

    /**
     * 停止任务并回写状态
     */
    private void stopTask(Long taskId, String lastMessage) {
        ConsignmentTaskEntity update = new ConsignmentTaskEntity();
        update.setId(taskId);
        update.setRunning(0);
        update.setLastMessage(lastMessage);
        consignmentTaskService.updateById(update);
        consignmentTaskRuntimeManager.stop(taskId);
    }

    /**
     * 统一构建启动/停止响应
     */
    private MonitorTaskExecuteRes buildExecuteRes(MonitorTaskExecuteRes res, boolean success, boolean running, String message) {
        res.setSuccess(success);
        res.setRunning(running);
        res.setMessage(message);
        res.setEndTime(LocalDateTime.now());
        return res;
    }

    /**
     * 校验任务请求参数
     */
    private void validateTaskReq(ConsignmentTaskSaveReq req) {
        if (!StringUtils.hasText(req.getTaskName())) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (req.getAccountId() == null) {
            throw new IllegalArgumentException("账号不能为空");
        }
        if (req.getDigitalCollectionGroupId() == null) {
            throw new IllegalArgumentException("目标商品不能为空");
        }
        if (!StringUtils.hasText(req.getCollectionName())) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        String taskType = normalizeTaskType(req.getTaskType());
        if (!TASK_TYPE_LIST.equals(taskType) && !TASK_TYPE_DELIST.equals(taskType)) {
            throw new IllegalArgumentException("任务类型不正确");
        }
        if (normalizeTargetCount(req.getTargetCount()) <= 0) {
            throw new IllegalArgumentException("目标数量必须大于0");
        }
        if (normalizeInterval(req.getIntervalMillis()) < 1000L) {
            throw new IllegalArgumentException("间隔时间不能小于1000毫秒");
        }
        if (TASK_TYPE_LIST.equals(taskType) && (req.getConsignPrice() == null || req.getConsignPrice().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("上架任务必须填写有效价格");
        }
        if (!StringUtils.hasText(autoPayPassword)) {
            throw new IllegalArgumentException("未配置ibox.auto-pay.password");
        }
    }

    /**
     * 归一化任务类型
     */
    private String normalizeTaskType(String taskType) {
        return StringUtils.hasText(taskType) ? taskType.trim().toUpperCase() : TASK_TYPE_LIST;
    }

    /**
     * 归一化目标数量
     */
    private int normalizeTargetCount(Integer targetCount) {
        return targetCount == null || targetCount <= 0 ? 1 : targetCount;
    }

    /**
     * 归一化执行间隔，最小1000毫秒
     */
    private long normalizeInterval(Long intervalMillis) {
        return intervalMillis == null || intervalMillis < 1000L ? 1000L : intervalMillis;
    }

    /**
     * 判断任务是否应立即停止
     */
    private boolean shouldStop(Long taskId) {
        return Thread.currentThread().isInterrupted() || !consignmentTaskRuntimeManager.isRunning(taskId);
    }

    /**
     * 统一处理休眠
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(millis, 0L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取账号，优先查缓存
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
     * 构建列表响应对象
     */
    private ConsignmentTaskRes buildRes(ConsignmentTaskEntity entity) {
        ConsignmentTaskRes res = new ConsignmentTaskRes();
        BeanUtils.copyProperties(entity, res);
        IboxAccountEntity account = getAccount(entity.getAccountId());
        if (account != null) {
            res.setAccountName(StringUtils.hasText(account.getPhone())
                    ? account.getAccountName() + "(" + account.getPhone() + ")"
                    : account.getAccountName() + "(ID:" + account.getId() + ")");
        }
        return res;
    }

    /**
     * 创建寄售订单请求体
     */
    public static class CreateConsignmentOrderReq {
        private Long digitalCollectionId;
        private BigDecimal price;
        private List<Integer> paymentPlatformCodes;
        private String consignPassword;

        public Long getDigitalCollectionId() {
            return digitalCollectionId;
        }

        public void setDigitalCollectionId(Long digitalCollectionId) {
            this.digitalCollectionId = digitalCollectionId;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public List<Integer> getPaymentPlatformCodes() {
            return paymentPlatformCodes;
        }

        public void setPaymentPlatformCodes(List<Integer> paymentPlatformCodes) {
            this.paymentPlatformCodes = paymentPlatformCodes;
        }

        public String getConsignPassword() {
            return consignPassword;
        }

        public void setConsignPassword(String consignPassword) {
            this.consignPassword = consignPassword;
        }
    }

    /**
     * 创建寄售订单响应体
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateConsignmentOrderRes {
        private String orderId;
        private Integer specialOrderType;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public Integer getSpecialOrderType() {
            return specialOrderType;
        }

        public void setSpecialOrderType(Integer specialOrderType) {
            this.specialOrderType = specialOrderType;
        }
    }

    /**
     * 下架寄售订单响应体
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CancelConsignmentOrderRes {
        private String orderUuid;
        private Integer specialOrderType;

        public String getOrderUuid() {
            return orderUuid;
        }

        public void setOrderUuid(String orderUuid) {
            this.orderUuid = orderUuid;
        }

        public Integer getSpecialOrderType() {
            return specialOrderType;
        }

        public void setSpecialOrderType(Integer specialOrderType) {
            this.specialOrderType = specialOrderType;
        }
    }

    /**
     * 单次寄售执行结果
     */
    private record ConsignmentActionResult(boolean success, String message) {
        private static ConsignmentActionResult success(String message) {
            return new ConsignmentActionResult(true, message);
        }

        private static ConsignmentActionResult fail(String message) {
            return new ConsignmentActionResult(false, message);
        }
    }
}
