package com.ibox.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.common.IboxAccountCache;
import com.ibox.common.SaleToPurchaseTaskRuntimeManager;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.SaleToPurchaseTaskEntity;
import com.ibox.manager.SaleToPurchaseTaskManager;
import com.ibox.model.req.web.SaleToPurchaseTaskPageReq;
import com.ibox.model.req.web.SaleToPurchaseTaskSaveReq;
import com.ibox.model.req.web.SaleToPurchaseTaskUpdateReq;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.PurchaseOrderItemRes;
import com.ibox.model.res.ibox.PurchaseOrderPageRes;
import com.ibox.model.res.ibox.UserDigitalCollectionGroupPageRes;
import com.ibox.model.res.ibox.UserDigitalCollectionItemRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.SaleToPurchaseTaskRes;
import com.ibox.service.IboxAccountService;
import com.ibox.service.SaleToPurchaseTaskService;
import com.ibox.utils.FeishuMessageUtil;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 出售求购任务实现
 */
@Slf4j
@Service
public class SaleToPurchaseTaskManagerImpl implements SaleToPurchaseTaskManager {

    private static final int STATUS_ENABLED = 1;

    private static final int PAGE_SIZE = 10;

    private static final String USER_DIGITAL_COLLECTION_GROUP_URL =
            UrlConstant.SAIL_API_BASE_URL + "/personal-center-service/users/digital-collection-groups/%s?pageNo=%s&pageSize=%s";

    @Autowired
    private SaleToPurchaseTaskService saleToPurchaseTaskService;

    @Autowired
    private IboxAccountService iboxAccountService;

    @Autowired
    private IboxAccountCache iboxAccountCache;

    @Autowired
    private IboxClient iboxClient;

    @Autowired
    private SaleToPurchaseTaskRuntimeManager saleToPurchaseTaskRuntimeManager;

    @Autowired
    private FeishuMessageUtil feishuMessageUtil;

    @Value("${ibox.auto-pay.password:}")
    private String autoPayPassword;

    @Override
    public PageResponse<SaleToPurchaseTaskRes> list(SaleToPurchaseTaskPageReq req) {
        LambdaQueryWrapper<SaleToPurchaseTaskEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(req.getTaskName()), SaleToPurchaseTaskEntity::getTaskName, req.getTaskName());
        queryWrapper.eq(req.getAccountId() != null, SaleToPurchaseTaskEntity::getAccountId, req.getAccountId());
        queryWrapper.eq(req.getStatus() != null, SaleToPurchaseTaskEntity::getStatus, req.getStatus());
        queryWrapper.eq(req.getRunning() != null, SaleToPurchaseTaskEntity::getRunning, req.getRunning());
        queryWrapper.orderByDesc(SaleToPurchaseTaskEntity::getId);
        IPage<SaleToPurchaseTaskEntity> page = saleToPurchaseTaskService.page(new Page<>(req.getPageNum(), req.getPageSize()), queryWrapper);
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::buildRes).toList());
    }

    @Override
    public SaleToPurchaseTaskRes getById(Long id) {
        SaleToPurchaseTaskEntity entity = saleToPurchaseTaskService.getById(id);
        return entity == null ? null : buildRes(entity);
    }

    @Override
    public boolean save(SaleToPurchaseTaskSaveReq req) {
        validateTaskReq(req);
        SaleToPurchaseTaskEntity entity = new SaleToPurchaseTaskEntity();
        BeanUtils.copyProperties(req, entity);
        entity.setMaxDealCount(normalizeMaxDealCount(req.getMaxDealCount()));
        entity.setIntervalMillis(normalizeInterval(req.getIntervalMillis()));
        entity.setPaymentPlatformCode(normalizePaymentPlatformCode(req.getPaymentPlatformCode()));
        entity.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        entity.setRunning(0);
        entity.setDealtCount(0);
        entity.setExecutedCount(0);
        entity.setSuccessCount(0);
        entity.setFailCount(0);
        entity.setLastMessage("未执行");
        return saleToPurchaseTaskService.save(entity);
    }

    @Override
    public boolean updateById(SaleToPurchaseTaskUpdateReq req) {
        validateTaskReq(req);
        SaleToPurchaseTaskEntity entity = new SaleToPurchaseTaskEntity();
        entity.setId(req.getId());
        entity.setTaskName(req.getTaskName());
        entity.setAccountId(req.getAccountId());
        entity.setDigitalCollectionGroupId(req.getDigitalCollectionGroupId());
        entity.setCollectionName(req.getCollectionName());
        entity.setMinAcceptPrice(req.getMinAcceptPrice());
        entity.setMaxDealCount(normalizeMaxDealCount(req.getMaxDealCount()));
        entity.setIntervalMillis(normalizeInterval(req.getIntervalMillis()));
        entity.setPaymentPlatformCode(normalizePaymentPlatformCode(req.getPaymentPlatformCode()));
        entity.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        entity.setRemark(req.getRemark());
        return saleToPurchaseTaskService.updateById(entity);
    }

    @Override
    public boolean removeById(Long id) {
        saleToPurchaseTaskRuntimeManager.stop(id);
        SaleToPurchaseTaskEntity update = new SaleToPurchaseTaskEntity();
        update.setId(id);
        update.setRunning(0);
        saleToPurchaseTaskService.updateById(update);
        return saleToPurchaseTaskService.removeById(id);
    }

    @Override
    public MonitorTaskExecuteRes startById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        SaleToPurchaseTaskEntity task = saleToPurchaseTaskService.getById(id);
        if (task == null) {
            return buildExecuteRes(res, false, false, "任务不存在");
        }
        if (!isEnabled(task)) {
            return buildExecuteRes(res, false, false, "任务未启用");
        }
        if (isTargetReached(task)) {
            return buildExecuteRes(res, false, false, "任务已达到最大成交数量");
        }

        IboxAccountEntity account = getAccount(task.getAccountId());
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            return buildExecuteRes(res, false, false, "账号不存在或 token 为空");
        }
        if (!StringUtils.hasText(autoPayPassword)) {
            return buildExecuteRes(res, false, false, "未配置寄售密码");
        }

        List<UserDigitalCollectionItemRes> sellableItems = filterSellableItems(fetchAllCollections(account.getBearerToken(), task.getDigitalCollectionGroupId()));
        if (CollectionUtils.isEmpty(sellableItems)) {
            return buildExecuteRes(res, false, false, "启动失败，没有可出售持仓");
        }

        boolean started = saleToPurchaseTaskRuntimeManager.start(id, () -> executeTaskLoop(id), sellableItems);
        if (!started) {
            return buildExecuteRes(res, false, true, "任务已在运行中");
        }

        SaleToPurchaseTaskEntity update = new SaleToPurchaseTaskEntity();
        update.setId(id);
        update.setRunning(1);
        update.setLastMessage("任务已启动，已加载可出售持仓:" + sellableItems.size());
        saleToPurchaseTaskService.updateById(update);
        return buildExecuteRes(res, true, true, "任务已启动");
    }

    @Override
    public MonitorTaskExecuteRes stopById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        boolean stopped = saleToPurchaseTaskRuntimeManager.stop(id);
        SaleToPurchaseTaskEntity update = new SaleToPurchaseTaskEntity();
        update.setId(id);
        update.setRunning(0);
        update.setLastMessage(stopped ? "任务已停止" : "任务未运行");
        saleToPurchaseTaskService.updateById(update);
        return buildExecuteRes(res, stopped, false, stopped ? "任务已停止" : "任务未运行");
    }

    private void executeTaskLoop(Long taskId) {
        if (shouldStop(taskId)) {
            return;
        }

        SaleToPurchaseTaskEntity task = saleToPurchaseTaskService.getById(taskId);
        if (task == null) {
            stopTask(taskId, "任务不存在");
            return;
        }
        if (!isEnabled(task)) {
            stopTask(taskId, "任务未启用");
            return;
        }
        if (isTargetReached(task)) {
            stopTask(taskId, "任务已达到最大成交数量");
            return;
        }

        IboxAccountEntity account = getAccount(task.getAccountId());
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            stopTask(taskId, "账号不存在或 token 为空");
            return;
        }

        SaleToPurchaseTaskRuntimeManager.SaleInventoryContext context = saleToPurchaseTaskRuntimeManager.getContext(taskId);
        if (context == null || context.isEmpty()) {
            stopTask(taskId, "缓存持仓为空，任务停止");
            return;
        }

        List<PurchaseOrderItemRes> purchaseOrders = fetchPurchaseOrders(task, account);
        if (CollectionUtils.isEmpty(purchaseOrders)) {
            updateTaskProgress(task, task.getExecutedCount(), task.getSuccessCount(), task.getFailCount(), "未匹配到可成交求购单");
            sleep(task.getIntervalMillis());
            return;
        }

        UserDigitalCollectionItemRes sellItem = context.peek();
        if (sellItem == null) {
            stopTask(taskId, "缓存持仓为空，任务停止");
            return;
        }

        PurchaseOrderItemRes matchedOrder = matchPurchaseOrder(task, purchaseOrders);
        if (matchedOrder == null) {
            updateTaskProgress(task, task.getExecutedCount(), task.getSuccessCount(), task.getFailCount(), "求购单价格未达到任务要求");
            sleep(task.getIntervalMillis());
            return;
        }

        DealResult dealResult = dealPurchaseOrder(task, account, sellItem, matchedOrder);
        int executedCount = defaultInt(task.getExecutedCount()) + 1;
        int successCount = defaultInt(task.getSuccessCount());
        int failCount = defaultInt(task.getFailCount());
        int dealtCount = defaultInt(task.getDealtCount());

        if (dealResult.success) {
            context.poll();
            successCount++;
            dealtCount++;
            updateDealSuccess(taskId, executedCount, successCount, failCount, dealtCount, "成交成功，求购价:" + safePrice(matchedOrder.getPrice()));
            sendDealSuccessMessage(task, account, sellItem, matchedOrder, context.size());
            if (isTargetReached(task.getMaxDealCount(), dealtCount)) {
                stopTask(taskId, "已达到最大成交数量");
                return;
            }
            if (context.isEmpty()) {
                stopTask(taskId, "缓存持仓已卖完，任务停止");
                return;
            }
        } else {
            failCount++;
            updateDealSuccess(taskId, executedCount, successCount, failCount, dealtCount, dealResult.message());
        }

        sleep(task.getIntervalMillis());
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
                log.warn("【出售求购任务】获取持仓失败，groupId:{}, pageNo:{}, response:{}", groupId, pageNo, JSONObject.toJSONString(response));
                break;
            }
            if (!CollectionUtils.isEmpty(response.getData().getList())) {
                result.addAll(response.getData().getList());
            }
            if (!Boolean.TRUE.equals(response.getData().getHasMore())) {
                break;
            }
            pageNo++;
            sleep(200L);
        }
        return result;
    }

    private List<UserDigitalCollectionItemRes> filterSellableItems(List<UserDigitalCollectionItemRes> allItems) {
        if (CollectionUtils.isEmpty(allItems)) {
            return List.of();
        }
        return allItems.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
//                .filter(item -> item.getDigitalCollectionStatus() != null && item.getDigitalCollectionStatus() == 0)
//                .filter(item -> item.getConsignmentStatus() == null || item.getConsignmentStatus() == 0)
//                .filter(item -> item.getEnableFreeMarket() == null || item.getEnableFreeMarket() == 1)
                .toList();
    }

    private List<PurchaseOrderItemRes> fetchPurchaseOrders(SaleToPurchaseTaskEntity task, IboxAccountEntity account) {
        String url = String.format(
                UrlConstant.PURCHASE_ORDER_LIST_URL,
                task.getDigitalCollectionGroupId(),
                1,
                PAGE_SIZE,
                account.getIboxUid()
        );
        IboxApiResponse<PurchaseOrderPageRes> response = iboxClient.get(
                account.getBearerToken(),
                url,
                null,
                new TypeReference<PurchaseOrderPageRes>() {
                }
        );
        if (response == null || response.getCode() == null || response.getCode() != 0 || response.getData() == null || CollectionUtils.isEmpty(response.getData().getList())) {
            return List.of();
        }
        Map<String, PurchaseOrderItemRes> deduplicatedMap = new LinkedHashMap<>();
        for (PurchaseOrderItemRes item : response.getData().getList()) {
            if (item == null || item.getId() == null || item.getOrderRelationId() == null) {
                continue;
            }
            deduplicatedMap.putIfAbsent(item.getId() + "_" + item.getOrderRelationId(), item);
        }
        return deduplicatedMap.values().stream()
                .sorted(Comparator.comparing(PurchaseOrderItemRes::getPrice, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .toList();
    }

    private PurchaseOrderItemRes matchPurchaseOrder(SaleToPurchaseTaskEntity task, List<PurchaseOrderItemRes> purchaseOrders) {
        BigDecimal minAcceptPrice = task.getMinAcceptPrice();
        return purchaseOrders.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getPrice() != null)
                .filter(item -> minAcceptPrice == null || item.getPrice().compareTo(minAcceptPrice) >= 0)
                .findFirst()
                .orElse(null);
    }

    private DealResult dealPurchaseOrder(SaleToPurchaseTaskEntity task,
                                         IboxAccountEntity account,
                                         UserDigitalCollectionItemRes sellItem,
                                         PurchaseOrderItemRes matchedOrder) {
        String url = String.format(
                UrlConstant.DEAL_PURCHASE_ORDER_URL,
                matchedOrder.getId(),
                matchedOrder.getOrderRelationId(),
                account.getIboxUid()
        );
        DealPurchaseOrderReq req = new DealPurchaseOrderReq();
        req.setPaymentPlatformCode(normalizePaymentPlatformCode(task.getPaymentPlatformCode()));
        req.setDigitalCollectionId(sellItem.getId());
        req.setConsignPassword(autoPayPassword);

        IboxApiResponse<Map<String, Object>> response;
        try {
            response = iboxClient.post(account.getBearerToken(), url, req, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("【出售求购任务】成交请求异常，taskId:{}, accountId:{}, digitalCollectionId:{}, purchaseOrderId:{}",
                    task.getId(), account.getId(), sellItem.getId(), matchedOrder.getId(), e);
            return DealResult.fail("成交请求异常:" + safeText(e.getMessage()));
        }

        log.info("【出售求购任务】成交响应，taskId:{}, accountId:{}, digitalCollectionId:{}, purchaseOrderId:{}, response:{}",
                task.getId(), account.getId(), sellItem.getId(), matchedOrder.getId(), JSONObject.toJSONString(response));
        if (response != null && response.getCode() != null && response.getCode() == 0) {
            return DealResult.success();
        }
        return DealResult.fail("成交失败:" + (response == null ? "response为空" : safeText(response.getMessage())));
    }

    private void updateTaskProgress(SaleToPurchaseTaskEntity task, Integer executedCount, Integer successCount, Integer failCount, String lastMessage) {
        SaleToPurchaseTaskEntity update = new SaleToPurchaseTaskEntity();
        update.setId(task.getId());
        update.setExecutedCount(defaultInt(executedCount));
        update.setSuccessCount(defaultInt(successCount));
        update.setFailCount(defaultInt(failCount));
        update.setLastMessage(lastMessage);
        saleToPurchaseTaskService.updateById(update);
    }

    private void updateDealSuccess(Long taskId, int executedCount, int successCount, int failCount, int dealtCount, String lastMessage) {
        SaleToPurchaseTaskEntity update = new SaleToPurchaseTaskEntity();
        update.setId(taskId);
        update.setExecutedCount(executedCount);
        update.setSuccessCount(successCount);
        update.setFailCount(failCount);
        update.setDealtCount(dealtCount);
        update.setLastMessage(lastMessage);
        saleToPurchaseTaskService.updateById(update);
    }

    private void sendDealSuccessMessage(SaleToPurchaseTaskEntity task,
                                        IboxAccountEntity account,
                                        UserDigitalCollectionItemRes sellItem,
                                        PurchaseOrderItemRes matchedOrder,
                                        int remainCount) {
        try {
            String message = "iBox出售求购成交成功，任务ID:" + task.getId()
                    + "，账号:" + safeText(account.getAccountName())
                    + "，商品:" + safeText(task.getCollectionName())
                    + "，持仓ID:" + safeText(sellItem.getId())
                    + "，成交价:" + safePrice(matchedOrder.getPrice())
                    + "，求购单ID:" + safeText(matchedOrder.getOrderUuid())
                    + "，剩余缓存持仓:" + remainCount;
            feishuMessageUtil.sendText(message, "sale-to-purchase-success:" + task.getId() + ":" + sellItem.getId(), 3);
        } catch (Exception e) {
            log.warn("【出售求购任务】发送飞书成功通知失败，taskId:{}, digitalCollectionId:{}", task.getId(), sellItem.getId(), e);
        }
    }

    private void stopTask(Long taskId, String lastMessage) {
        SaleToPurchaseTaskEntity update = new SaleToPurchaseTaskEntity();
        update.setId(taskId);
        update.setRunning(0);
        update.setLastMessage(lastMessage);
        saleToPurchaseTaskService.updateById(update);
        saleToPurchaseTaskRuntimeManager.stop(taskId);
    }

    private void validateTaskReq(SaleToPurchaseTaskSaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
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
        if (req.getMinAcceptPrice() == null || req.getMinAcceptPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("最低接受价格不能为空且不能小于0");
        }
        if (normalizeMaxDealCount(req.getMaxDealCount()) <= 0) {
            throw new IllegalArgumentException("最大成交数量必须大于0");
        }
        if (normalizeInterval(req.getIntervalMillis()) < 100L) {
            throw new IllegalArgumentException("轮询间隔不能小于100毫秒");
        }
    }

    private MonitorTaskExecuteRes buildExecuteRes(MonitorTaskExecuteRes res, boolean success, boolean running, String message) {
        res.setSuccess(success);
        res.setRunning(running);
        res.setMessage(message);
        res.setEndTime(LocalDateTime.now());
        return res;
    }

    private boolean isEnabled(SaleToPurchaseTaskEntity task) {
        return task.getStatus() != null && task.getStatus() == STATUS_ENABLED;
    }

    private boolean isTargetReached(SaleToPurchaseTaskEntity task) {
        return isTargetReached(task.getMaxDealCount(), defaultInt(task.getDealtCount()));
    }

    private boolean isTargetReached(Integer maxDealCount, int dealtCount) {
        int maxCount = normalizeMaxDealCount(maxDealCount);
        return maxCount > 0 && dealtCount >= maxCount;
    }

    private Integer normalizePaymentPlatformCode(Integer paymentPlatformCode) {
        return paymentPlatformCode == null ? 30 : paymentPlatformCode;
    }

    private int normalizeMaxDealCount(Integer maxDealCount) {
        return maxDealCount == null || maxDealCount <= 0 ? 1 : maxDealCount;
    }

    private long normalizeInterval(Long intervalMillis) {
        return intervalMillis == null || intervalMillis < 100L ? 1000L : intervalMillis;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean shouldStop(Long taskId) {
        return Thread.currentThread().isInterrupted() || !saleToPurchaseTaskRuntimeManager.isRunning(taskId);
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

    private void sleep(Long millis) {
        try {
            Thread.sleep(Math.max(normalizeInterval(millis), 0L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private SaleToPurchaseTaskRes buildRes(SaleToPurchaseTaskEntity entity) {
        SaleToPurchaseTaskRes res = new SaleToPurchaseTaskRes();
        BeanUtils.copyProperties(entity, res);
        IboxAccountEntity account = getAccount(entity.getAccountId());
        if (account != null) {
            res.setAccountName(StringUtils.hasText(account.getPhone())
                    ? account.getAccountName() + "(" + account.getPhone() + ")"
                    : account.getAccountName() + "(ID:" + account.getId() + ")");
        }
        return res;
    }

    private String safeText(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : "-";
    }

    private String safePrice(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    public static class DealPurchaseOrderReq {
        private Integer paymentPlatformCode;
        private Long digitalCollectionId;
        private String consignPassword;

        public Integer getPaymentPlatformCode() {
            return paymentPlatformCode;
        }

        public void setPaymentPlatformCode(Integer paymentPlatformCode) {
            this.paymentPlatformCode = paymentPlatformCode;
        }

        public Long getDigitalCollectionId() {
            return digitalCollectionId;
        }

        public void setDigitalCollectionId(Long digitalCollectionId) {
            this.digitalCollectionId = digitalCollectionId;
        }

        public String getConsignPassword() {
            return consignPassword;
        }

        public void setConsignPassword(String consignPassword) {
            this.consignPassword = consignPassword;
        }
    }

    private static class DealResult {
        private final boolean success;
        private final String message;

        private DealResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static DealResult success() {
            return new DealResult(true, "成交成功");
        }

        public static DealResult fail(String message) {
            return new DealResult(false, message);
        }


        public String message() {
            return message;
        }
    }
}
