package com.ibox.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.common.IboxAccountCache;
import com.ibox.common.LoopConsignmentTaskRuntimeManager;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.LoopConsignmentTaskEntity;
import com.ibox.manager.LoopConsignmentTaskManager;
import com.ibox.model.req.web.LoopConsignmentTaskPageReq;
import com.ibox.model.req.web.LoopConsignmentTaskSaveReq;
import com.ibox.model.req.web.LoopConsignmentTaskUpdateReq;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.UserDigitalCollectionGroupPageRes;
import com.ibox.model.res.ibox.UserDigitalCollectionItemRes;
import com.ibox.model.res.web.LoopConsignmentTaskRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.service.IboxAccountService;
import com.ibox.service.LoopConsignmentTaskService;
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

/**
 * 循环上下架任务实现。
 */
@Slf4j
@Service
public class LoopConsignmentTaskManagerImpl implements LoopConsignmentTaskManager {

    private static final int STATUS_ENABLED = 1;

    private static final long DEFAULT_LIST_ITEM_INTERVAL_MILLIS = 3000L;

    private static final long DEFAULT_AFTER_LIST_TO_DELIST_INTERVAL_MILLIS = 240000L;

    private static final long DEFAULT_DELIST_ITEM_INTERVAL_MILLIS = 2000L;

    private static final long DEFAULT_AFTER_DELIST_TO_LIST_INTERVAL_MILLIS = 240000L;

    private static final int PAGE_SIZE = 20;

    private static final String STAGE_LISTING = "LISTING";

    private static final String STAGE_WAIT_DELIST = "WAIT_DELIST";

    private static final String STAGE_DELISTING = "DELISTING";

    private static final String STAGE_WAIT_LIST = "WAIT_LIST";

    private static final String USER_DIGITAL_COLLECTION_GROUP_URL =
            UrlConstant.SAIL_API_BASE_URL + "/personal-center-service/users/digital-collection-groups/%s?pageNo=%s&pageSize=%s";

    private static final String CREATE_CONSIGNMENT_ORDER_URL =
            UrlConstant.SAIL_API_BASE_URL + "/order-create-service/consignment-orders";

    private static final String CANCEL_CONSIGNMENT_ORDER_URL =
            UrlConstant.SAIL_API_BASE_URL + "/order-service/consign-orders/%s/cancel";

    @Autowired
    private LoopConsignmentTaskService loopConsignmentTaskService;

    @Autowired
    private IboxAccountService iboxAccountService;

    @Autowired
    private IboxAccountCache iboxAccountCache;

    @Autowired
    private IboxClient iboxClient;

    @Autowired
    private LoopConsignmentTaskRuntimeManager loopConsignmentTaskRuntimeManager;

    @Value("${ibox.auto-pay.password:}")
    private String autoPayPassword;

    @Override
    public PageResponse<LoopConsignmentTaskRes> list(LoopConsignmentTaskPageReq req) {
        LambdaQueryWrapper<LoopConsignmentTaskEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(req.getTaskName()), LoopConsignmentTaskEntity::getTaskName, req.getTaskName());
        queryWrapper.eq(req.getAccountId() != null, LoopConsignmentTaskEntity::getAccountId, req.getAccountId());
        queryWrapper.eq(StringUtils.hasText(req.getCurrentStage()), LoopConsignmentTaskEntity::getCurrentStage, req.getCurrentStage());
        queryWrapper.eq(req.getStatus() != null, LoopConsignmentTaskEntity::getStatus, req.getStatus());
        queryWrapper.eq(req.getRunning() != null, LoopConsignmentTaskEntity::getRunning, req.getRunning());
        queryWrapper.orderByDesc(LoopConsignmentTaskEntity::getId);
        IPage<LoopConsignmentTaskEntity> page = loopConsignmentTaskService.page(new Page<>(req.getPageNum(), req.getPageSize()), queryWrapper);
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::buildRes).toList());
    }

    @Override
    public LoopConsignmentTaskRes getById(Long id) {
        LoopConsignmentTaskEntity entity = loopConsignmentTaskService.getById(id);
        return entity == null ? null : buildRes(entity);
    }

    @Override
    public boolean save(LoopConsignmentTaskSaveReq req) {
        validateTaskReq(req);
        LoopConsignmentTaskEntity entity = new LoopConsignmentTaskEntity();
        BeanUtils.copyProperties(req, entity);
        entity.setListItemIntervalMillis(normalizeListItemInterval(req.getListItemIntervalMillis()));
        entity.setAfterListToDelistIntervalMillis(normalizeAfterListToDelistInterval(req.getAfterListToDelistIntervalMillis()));
        entity.setDelistItemIntervalMillis(normalizeDelistItemInterval(req.getDelistItemIntervalMillis()));
        entity.setAfterDelistToListIntervalMillis(normalizeAfterDelistToListInterval(req.getAfterDelistToListIntervalMillis()));
        entity.setCurrentStage(STAGE_LISTING);
        entity.setNextExecuteTime(LocalDateTime.now());
        entity.setExecutedRoundCount(0);
        entity.setExecutedCount(0);
        entity.setSuccessCount(0);
        entity.setFailCount(0);
        entity.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        entity.setRunning(0);
        entity.setLastMessage("未执行");
        return loopConsignmentTaskService.save(entity);
    }

    @Override
    public boolean updateById(LoopConsignmentTaskUpdateReq req) {
        validateTaskReq(req);
        LoopConsignmentTaskEntity entity = new LoopConsignmentTaskEntity();
        entity.setId(req.getId());
        entity.setTaskName(req.getTaskName());
        entity.setAccountId(req.getAccountId());
        entity.setDigitalCollectionGroupId(req.getDigitalCollectionGroupId());
        entity.setCollectionName(req.getCollectionName());
        entity.setConsignPrice(req.getConsignPrice());
        entity.setListItemIntervalMillis(normalizeListItemInterval(req.getListItemIntervalMillis()));
        entity.setAfterListToDelistIntervalMillis(normalizeAfterListToDelistInterval(req.getAfterListToDelistIntervalMillis()));
        entity.setDelistItemIntervalMillis(normalizeDelistItemInterval(req.getDelistItemIntervalMillis()));
        entity.setAfterDelistToListIntervalMillis(normalizeAfterDelistToListInterval(req.getAfterDelistToListIntervalMillis()));
        entity.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        entity.setRemark(req.getRemark());
        return loopConsignmentTaskService.updateById(entity);
    }

    @Override
    public boolean removeById(Long id) {
        loopConsignmentTaskRuntimeManager.stop(id);
        LoopConsignmentTaskEntity update = new LoopConsignmentTaskEntity();
        update.setId(id);
        update.setRunning(0);
        loopConsignmentTaskService.updateById(update);
        return loopConsignmentTaskService.removeById(id);
    }

    @Override
    public MonitorTaskExecuteRes startById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        LoopConsignmentTaskEntity task = loopConsignmentTaskService.getById(id);
        if (task == null) {
            return buildExecuteRes(res, false, false, "任务不存在");
        }
        if (!isEnabled(task)) {
            return buildExecuteRes(res, false, false, "任务未启用");
        }

        IboxAccountEntity account = getAccount(task.getAccountId());
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            return buildExecuteRes(res, false, false, "账号不存在或 token 为空");
        }
        if (!StringUtils.hasText(autoPayPassword)) {
            return buildExecuteRes(res, false, false, "未配置寄售密码");
        }

        boolean started = loopConsignmentTaskRuntimeManager.start(id, () -> executeTaskLoop(id));
        if (!started) {
            return buildExecuteRes(res, false, true, "任务已在运行中");
        }

        LoopConsignmentTaskEntity update = new LoopConsignmentTaskEntity();
        update.setId(id);
        update.setRunning(1);
        update.setLastMessage("任务已启动");
        update.setNextExecuteTime(LocalDateTime.now());
        if (!StringUtils.hasText(task.getCurrentStage())) {
            update.setCurrentStage(STAGE_LISTING);
        }
        loopConsignmentTaskService.updateById(update);
        return buildExecuteRes(res, true, true, "任务已启动");
    }

    @Override
    public MonitorTaskExecuteRes stopById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        boolean stopped = loopConsignmentTaskRuntimeManager.stop(id);
        LoopConsignmentTaskEntity update = new LoopConsignmentTaskEntity();
        update.setId(id);
        update.setRunning(0);
        update.setLastMessage(stopped ? "任务已停止" : "任务未运行");
        loopConsignmentTaskService.updateById(update);
        return buildExecuteRes(res, stopped, false, stopped ? "任务已停止" : "任务未运行");
    }

    private void executeTaskLoop(Long taskId) {
        if (shouldStop(taskId)) {
            return;
        }

        LoopConsignmentTaskEntity task = loopConsignmentTaskService.getById(taskId);
        if (task == null) {
            stopTask(taskId, "任务不存在");
            return;
        }
        if (!isEnabled(task)) {
            stopTask(taskId, "任务未启用");
            return;
        }

        IboxAccountEntity account = getAccount(task.getAccountId());
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            stopTask(taskId, "账号不存在或 token 为空");
            return;
        }

        if (task.getNextExecuteTime() != null && LocalDateTime.now().isBefore(task.getNextExecuteTime())) {
            sleep(1000L);
            return;
        }

        List<UserDigitalCollectionItemRes> allItems = fetchAllCollections(account.getBearerToken(), task.getDigitalCollectionGroupId());
        if (CollectionUtils.isEmpty(allItems)) {
            stopTask(taskId, "仓库没有商品，任务停止");
            return;
        }

        String stage = normalizeStage(task.getCurrentStage());
        if (STAGE_LISTING.equals(stage)) {
            handleListingStage(task, account, allItems);
            return;
        }
        if (STAGE_DELISTING.equals(stage)) {
            handleDelistingStage(task, account, allItems);
            return;
        }
        if (STAGE_WAIT_DELIST.equals(stage)) {
            switchToStage(taskId, STAGE_DELISTING, LocalDateTime.now(), "等待结束，开始全部下架");
            sleep(300L);
            return;
        }
        if (STAGE_WAIT_LIST.equals(stage)) {
            switchToStage(taskId, STAGE_LISTING, LocalDateTime.now(), "等待结束，开始全部上架");
            sleep(300L);
            return;
        }

        switchToStage(taskId, STAGE_LISTING, LocalDateTime.now(), "阶段异常，重置为全部上架");
        sleep(300L);
    }

    private void handleListingStage(LoopConsignmentTaskEntity task, IboxAccountEntity account, List<UserDigitalCollectionItemRes> allItems) {
        List<UserDigitalCollectionItemRes> candidates = allItems.stream()
                .filter(item -> item != null && Integer.valueOf(0).equals(item.getDigitalCollectionStatus()))
                .toList();
        if (CollectionUtils.isEmpty(candidates)) {
            LocalDateTime nextExecuteTime = LocalDateTime.now()
                    .plusNanos(normalizeAfterListToDelistInterval(task.getAfterListToDelistIntervalMillis()) * 1_000_000L);
            switchToStage(task.getId(), STAGE_WAIT_DELIST, nextExecuteTime, "当前无可上架商品，等待执行全部下架");
            return;
        }

        int successCount = defaultInt(task.getSuccessCount());
        int failCount = defaultInt(task.getFailCount());
        int executedCount = defaultInt(task.getExecutedCount());

        for (UserDigitalCollectionItemRes item : candidates) {
            if (shouldStop(task.getId())) {
                return;
            }
            ConsignmentActionResult result = createConsignmentOrder(account.getBearerToken(), item, task);
            executedCount++;
            if (result.isSuccess()) {
                successCount++;
            } else {
                failCount++;
            }
            updateTaskProgress(task.getId(), executedCount, successCount, failCount, result.getMessage());
            sleep(normalizeListItemInterval(task.getListItemIntervalMillis()));
        }

        LocalDateTime nextExecuteTime = LocalDateTime.now()
                .plusNanos(normalizeAfterListToDelistInterval(task.getAfterListToDelistIntervalMillis()) * 1_000_000L);
        switchToStage(task.getId(), STAGE_WAIT_DELIST, nextExecuteTime, "全部上架完成，等待执行全部下架");
    }

    private void handleDelistingStage(LoopConsignmentTaskEntity task, IboxAccountEntity account, List<UserDigitalCollectionItemRes> allItems) {
        List<UserDigitalCollectionItemRes> candidates = allItems.stream()
                .filter(item -> item != null
                        && Integer.valueOf(2).equals(item.getDigitalCollectionStatus())
                        && item.getOrderId() != null)
                .toList();
        if (CollectionUtils.isEmpty(candidates)) {
            LocalDateTime nextExecuteTime = LocalDateTime.now()
                    .plusNanos(normalizeAfterDelistToListInterval(task.getAfterDelistToListIntervalMillis()) * 1_000_000L);
            incrementRoundAndSwitchStage(task, STAGE_WAIT_LIST, nextExecuteTime, "当前无可下架商品，等待下一轮全部上架");
            return;
        }

        int successCount = defaultInt(task.getSuccessCount());
        int failCount = defaultInt(task.getFailCount());
        int executedCount = defaultInt(task.getExecutedCount());

        for (UserDigitalCollectionItemRes item : candidates) {
            if (shouldStop(task.getId())) {
                return;
            }
            ConsignmentActionResult result = cancelConsignmentOrder(account.getBearerToken(), item);
            executedCount++;
            if (result.isSuccess()) {
                successCount++;
            } else {
                failCount++;
            }
            updateTaskProgress(task.getId(), executedCount, successCount, failCount, result.getMessage());
            sleep(normalizeDelistItemInterval(task.getDelistItemIntervalMillis()));
        }

        LocalDateTime nextExecuteTime = LocalDateTime.now()
                .plusNanos(normalizeAfterDelistToListInterval(task.getAfterDelistToListIntervalMillis()) * 1_000_000L);
        incrementRoundAndSwitchStage(task, STAGE_WAIT_LIST, nextExecuteTime, "全部下架完成，等待下一轮全部上架");
    }

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
                log.warn("【循环上下架任务】获取持有商品列表失败，groupId:{}, pageNo:{}, response:{}",
                        groupId, pageNo, JSONObject.toJSONString(response));
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

    private ConsignmentActionResult createConsignmentOrder(String token, UserDigitalCollectionItemRes item, LoopConsignmentTaskEntity task) {
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
            return ConsignmentActionResult.fail("上架失败，response 为空，商品ID:" + item.getId());
        }
        if (response.getCode() != null && response.getCode() == 0 && response.getData() != null) {
            return ConsignmentActionResult.success("上架成功，商品ID:" + item.getId() + "，订单ID:" + response.getData().getOrderId());
        }
        return ConsignmentActionResult.fail("上架失败，商品ID:" + item.getId() + "，response:" + JSONObject.toJSONString(response));
    }

    private ConsignmentActionResult cancelConsignmentOrder(String token, UserDigitalCollectionItemRes item) {
        if (item.getOrderId() == null) {
            return ConsignmentActionResult.fail("下架失败，orderId 为空，商品ID:" + item.getId());
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
            return ConsignmentActionResult.fail("下架失败，response 为空，商品ID:" + item.getId() + "，orderId:" + item.getOrderId());
        }
        if (response.getCode() != null && response.getCode() == 0) {
            return ConsignmentActionResult.success("下架成功，商品ID:" + item.getId() + "，orderId:" + item.getOrderId());
        }
        return ConsignmentActionResult.fail("下架失败，商品ID:" + item.getId() + "，response:" + JSONObject.toJSONString(response));
    }

    private void updateTaskProgress(Long taskId, int executedCount, int successCount, int failCount, String lastMessage) {
        LoopConsignmentTaskEntity update = new LoopConsignmentTaskEntity();
        update.setId(taskId);
        update.setExecutedCount(executedCount);
        update.setSuccessCount(successCount);
        update.setFailCount(failCount);
        update.setLastMessage(lastMessage);
        loopConsignmentTaskService.updateById(update);
    }

    private void switchToStage(Long taskId, String stage, LocalDateTime nextExecuteTime, String lastMessage) {
        LoopConsignmentTaskEntity update = new LoopConsignmentTaskEntity();
        update.setId(taskId);
        update.setCurrentStage(stage);
        update.setNextExecuteTime(nextExecuteTime);
        update.setLastMessage(lastMessage);
        loopConsignmentTaskService.updateById(update);
    }

    private void incrementRoundAndSwitchStage(LoopConsignmentTaskEntity task, String stage, LocalDateTime nextExecuteTime, String lastMessage) {
        LoopConsignmentTaskEntity update = new LoopConsignmentTaskEntity();
        update.setId(task.getId());
        update.setCurrentStage(stage);
        update.setNextExecuteTime(nextExecuteTime);
        update.setExecutedRoundCount(defaultInt(task.getExecutedRoundCount()) + 1);
        update.setLastMessage(lastMessage);
        loopConsignmentTaskService.updateById(update);
    }

    private void stopTask(Long taskId, String lastMessage) {
        LoopConsignmentTaskEntity update = new LoopConsignmentTaskEntity();
        update.setId(taskId);
        update.setRunning(0);
        update.setLastMessage(lastMessage);
        loopConsignmentTaskService.updateById(update);
        loopConsignmentTaskRuntimeManager.stop(taskId);
    }

    private MonitorTaskExecuteRes buildExecuteRes(MonitorTaskExecuteRes res, boolean success, boolean running, String message) {
        res.setSuccess(success);
        res.setRunning(running);
        res.setMessage(message);
        res.setEndTime(LocalDateTime.now());
        return res;
    }

    private void validateTaskReq(LoopConsignmentTaskSaveReq req) {
        if (!StringUtils.hasText(req.getTaskName())) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (req.getAccountId() == null) {
            throw new IllegalArgumentException("账号不能为空");
        }
        if (req.getDigitalCollectionGroupId() == null) {
            throw new IllegalArgumentException("商品不能为空");
        }
        if (!StringUtils.hasText(req.getCollectionName())) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (req.getConsignPrice() == null || req.getConsignPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("上架价格必须大于0");
        }
        if (normalizeListItemInterval(req.getListItemIntervalMillis()) < 1000L) {
            throw new IllegalArgumentException("上架间隔不能小于1000毫秒");
        }
        if (normalizeAfterListToDelistInterval(req.getAfterListToDelistIntervalMillis()) < 1000L) {
            throw new IllegalArgumentException("全部上架后执行下架间隔不能小于1000毫秒");
        }
        if (normalizeDelistItemInterval(req.getDelistItemIntervalMillis()) < 1000L) {
            throw new IllegalArgumentException("下架间隔不能小于1000毫秒");
        }
        if (normalizeAfterDelistToListInterval(req.getAfterDelistToListIntervalMillis()) < 1000L) {
            throw new IllegalArgumentException("全部下架后执行上架间隔不能小于1000毫秒");
        }
        if (!StringUtils.hasText(autoPayPassword)) {
            throw new IllegalArgumentException("未配置 ibox.auto-pay.password");
        }
    }

    private boolean isEnabled(LoopConsignmentTaskEntity task) {
        return task.getStatus() != null && task.getStatus() == STATUS_ENABLED;
    }

    private String normalizeStage(String currentStage) {
        if (!StringUtils.hasText(currentStage)) {
            return STAGE_LISTING;
        }
        return currentStage.trim().toUpperCase();
    }

    private long normalizeListItemInterval(Long intervalMillis) {
        return intervalMillis == null || intervalMillis < 1000L ? DEFAULT_LIST_ITEM_INTERVAL_MILLIS : intervalMillis;
    }

    private long normalizeAfterListToDelistInterval(Long intervalMillis) {
        return intervalMillis == null || intervalMillis < 1000L ? DEFAULT_AFTER_LIST_TO_DELIST_INTERVAL_MILLIS : intervalMillis;
    }

    private long normalizeDelistItemInterval(Long intervalMillis) {
        return intervalMillis == null || intervalMillis < 1000L ? DEFAULT_DELIST_ITEM_INTERVAL_MILLIS : intervalMillis;
    }

    private long normalizeAfterDelistToListInterval(Long intervalMillis) {
        return intervalMillis == null || intervalMillis < 1000L ? DEFAULT_AFTER_DELIST_TO_LIST_INTERVAL_MILLIS : intervalMillis;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean shouldStop(Long taskId) {
        return Thread.currentThread().isInterrupted() || !loopConsignmentTaskRuntimeManager.isRunning(taskId);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(millis, 0L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    private LoopConsignmentTaskRes buildRes(LoopConsignmentTaskEntity entity) {
        LoopConsignmentTaskRes res = new LoopConsignmentTaskRes();
        BeanUtils.copyProperties(entity, res);
        IboxAccountEntity account = getAccount(entity.getAccountId());
        if (account != null) {
            res.setAccountName(StringUtils.hasText(account.getPhone())
                    ? account.getAccountName() + "(" + account.getPhone() + ")"
                    : account.getAccountName() + "(ID:" + account.getId() + ")");
        }
        return res;
    }

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

    private static class ConsignmentActionResult {
        private final boolean success;
        private final String message;

        private ConsignmentActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ConsignmentActionResult success(String message) {
            return new ConsignmentActionResult(true, message);
        }

        public static ConsignmentActionResult fail(String message) {
            return new ConsignmentActionResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
