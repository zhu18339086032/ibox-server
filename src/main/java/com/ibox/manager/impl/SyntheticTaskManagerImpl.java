package com.ibox.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.common.IboxAccountCache;
import com.ibox.common.SyntheticTaskRuntimeManager;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.SyntheticTaskEntity;
import com.ibox.entity.SyntheticTaskRecordEntity;
import com.ibox.manager.SyntheticTaskManager;
import com.ibox.model.req.ibox.SyntheticConfirmReq;
import com.ibox.model.req.web.SyntheticTaskPageReq;
import com.ibox.model.req.web.SyntheticTaskRecordPageReq;
import com.ibox.model.req.web.SyntheticTaskSaveReq;
import com.ibox.model.req.web.SyntheticTaskUpdateReq;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.IboxPageDataRes;
import com.ibox.model.res.ibox.SyntheticAlbumRes;
import com.ibox.model.res.ibox.SyntheticBurnAlbumGroupRes;
import com.ibox.model.res.ibox.SyntheticCenterRes;
import com.ibox.model.res.ibox.SyntheticConfirmResultRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.SyntheticTaskRecordRes;
import com.ibox.model.res.web.SyntheticTaskRes;
import com.ibox.service.IboxAccountService;
import com.ibox.service.SyntheticTaskRecordService;
import com.ibox.service.SyntheticTaskService;
import com.ibox.utils.FeishuMessageUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 活动合成任务管理器实现。
 *
 * <p>当前版本采用“单任务单账号”的执行模型：</p>
 * <p>1. 前端只能选择一个账号。</p>
 * <p>2. 预热阶段查询 center detail 使用任务账号 token。</p>
 * <p>3. 最终 confirm 合成使用同一个任务账号 token。</p>
 * <p>4. prepared context 显式绑定 accountId，避免串号。</p>
 *
 * <p>同时保留现有联调用的两段测试逻辑：</p>
 * <p>1. confirm 失败分支仍然会模拟累加成功次数。</p>
 * <p>2. 自动匹配材料仍然保持“只要 album != null 就命中”的逻辑。</p>
 */
@Slf4j
@Service
public class SyntheticTaskManagerImpl implements SyntheticTaskManager {

    private static final String MATERIAL_STRATEGY_MANUAL = "MANUAL";
    private static final String MATERIAL_STRATEGY_AUTO_MATCH = "AUTO_MATCH";
    private static final long PREPARE_AHEAD_MILLIS = 30_000L;
    private static final long PREPARED_CONTEXT_EXPIRE_MILLIS = 60_000L;

    private final ConcurrentHashMap<Long, PreparedExecutionContext> preparedContextMap = new ConcurrentHashMap<>();

    @Autowired
    private SyntheticTaskService syntheticTaskService;

    @Autowired
    private SyntheticTaskRecordService syntheticTaskRecordService;

    @Autowired
    private IboxAccountService iboxAccountService;

    @Autowired
    private IboxAccountCache iboxAccountCache;

    @Autowired
    private IboxClient iboxClient;

    @Autowired
    private SyntheticTaskRuntimeManager syntheticTaskRuntimeManager;

    @Autowired
    private FeishuMessageUtil feishuMessageUtil;

    @Override
    public PageResponse<SyntheticTaskRes> list(SyntheticTaskPageReq req) {
        LambdaQueryWrapper<SyntheticTaskEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(req.getTaskName()), SyntheticTaskEntity::getTaskName, req.getTaskName());
        queryWrapper.eq(req.getStatus() != null, SyntheticTaskEntity::getStatus, req.getStatus());
        queryWrapper.eq(req.getRunning() != null, SyntheticTaskEntity::getRunning, req.getRunning());
        queryWrapper.eq(req.getActivityId() != null, SyntheticTaskEntity::getActivityId, req.getActivityId());
        queryWrapper.apply(req.getAccountId() != null, "FIND_IN_SET({0}, account_ids)", req.getAccountId());
        queryWrapper.orderByDesc(SyntheticTaskEntity::getId);
        IPage<SyntheticTaskEntity> page = syntheticTaskService.page(new Page<>(req.getPageNum(), req.getPageSize()), queryWrapper);
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::buildTaskRes).toList());
    }

    @Override
    public SyntheticTaskRes getById(Long id) {
        SyntheticTaskEntity entity = syntheticTaskService.getById(id);
        return entity == null ? null : buildTaskRes(entity);
    }

    @Override
    public PageResponse<SyntheticTaskRecordRes> recordList(SyntheticTaskRecordPageReq req) {
        LambdaQueryWrapper<SyntheticTaskRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(req.getTaskId() != null, SyntheticTaskRecordEntity::getTaskId, req.getTaskId());
        queryWrapper.eq(req.getAccountId() != null, SyntheticTaskRecordEntity::getAccountId, req.getAccountId());
        queryWrapper.eq(req.getSuccessFlag() != null, SyntheticTaskRecordEntity::getSuccessFlag, req.getSuccessFlag());
        queryWrapper.orderByDesc(SyntheticTaskRecordEntity::getId);
        IPage<SyntheticTaskRecordEntity> page = syntheticTaskRecordService.page(new Page<>(req.getPageNum(), req.getPageSize()), queryWrapper);
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::buildRecordRes).toList());
    }

    @Override
    public boolean save(SyntheticTaskSaveReq req) {
        applyDefaults(req);
        validateTask(req.getTaskName(), req.getActivityId(), req.getSyntheticId(), req.getAccountIds(),
            req.getExecuteStartTime(), req.getExecuteEndTime(), req.getMaxSyntheticCount(), req.getIntervalMillis());

        SyntheticTaskEntity entity = new SyntheticTaskEntity();
        BeanUtils.copyProperties(req, entity);
        entity.setAccountIds(joinLongValues(req.getAccountIds()));
        entity.setMaterialAlbumIds(joinLongValues(req.getMaterialAlbumIds()));
        entity.setMaterialStrategy(normalizeMaterialStrategy(req.getMaterialStrategy()));
        entity.setSuccessCount(0);
        entity.setRunning(0);
        entity.setLastResultCode(null);
        entity.setLastResultMessage(null);
        boolean saved = syntheticTaskService.save(entity);
        if (saved && entity.getId() != null) {
            prepareContextAfterMutation(entity.getId());
        }
        return saved;
    }

    @Override
    public boolean updateById(SyntheticTaskUpdateReq req) {
        applyDefaults(req);
        validateTask(req.getTaskName(), req.getActivityId(), req.getSyntheticId(), req.getAccountIds(),
            req.getExecuteStartTime(), req.getExecuteEndTime(), req.getMaxSyntheticCount(), req.getIntervalMillis());

        SyntheticTaskEntity origin = syntheticTaskService.getById(req.getId());
        if (origin == null) {
            throw new IllegalArgumentException("任务不存在");
        }

        preparedContextMap.remove(req.getId());

        SyntheticTaskEntity entity = new SyntheticTaskEntity();
        entity.setId(req.getId());
        entity.setTaskName(req.getTaskName());
        entity.setActivityId(req.getActivityId());
        entity.setActivityName(req.getActivityName());
        entity.setSyntheticId(req.getSyntheticId());
        entity.setChannelName(req.getChannelName());
        entity.setAccountIds(joinLongValues(req.getAccountIds()));
        entity.setTargetGroupId(req.getTargetGroupId());
        entity.setTargetGroupName(req.getTargetGroupName());
        entity.setMaterialStrategy(normalizeMaterialStrategy(req.getMaterialStrategy()));
        entity.setMaterialAlbumIds(joinLongValues(req.getMaterialAlbumIds()));
        entity.setMaxSyntheticCount(req.getMaxSyntheticCount());
        entity.setExecuteStartTime(req.getExecuteStartTime());
        entity.setExecuteEndTime(req.getExecuteEndTime());
        entity.setIntervalMillis(req.getIntervalMillis());
        entity.setStatus(req.getStatus());
        entity.setRemark(req.getRemark());
        boolean updated = syntheticTaskService.updateById(entity);
        if (updated) {
            prepareContextAfterMutation(entity.getId());
        }
        return updated;
    }

    @Override
    public boolean removeById(Long id) {
        syntheticTaskRuntimeManager.stop(id);
        preparedContextMap.remove(id);

        SyntheticTaskEntity update = new SyntheticTaskEntity();
        update.setId(id);
        update.setRunning(0);
        syntheticTaskService.updateById(update);
        return syntheticTaskService.removeById(id);
    }

    @Override
    public MonitorTaskExecuteRes startById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        SyntheticTaskEntity task = syntheticTaskService.getById(id);
        if (task == null) {
            fillExecuteRes(res, false, false, "任务不存在");
            return res;
        }
        if (task.getStatus() == null || task.getStatus() != 1) {
            fillExecuteRes(res, false, false, "任务未启用");
            return res;
        }
        if (extractSingleAccountId(task) == null) {
            fillExecuteRes(res, false, false, "任务未配置可用账号");
            return res;
        }
        if (isTargetReached(task)) {
            fillExecuteRes(res, false, false, "任务已达到成功上限");
            return res;
        }

        boolean started = syntheticTaskRuntimeManager.start(id, () -> executeTaskLoop(id));
        if (started) {
            SyntheticTaskEntity update = new SyntheticTaskEntity();
            update.setId(id);
            update.setRunning(1);
            syntheticTaskService.updateById(update);
            fillExecuteRes(res, true, true, "任务已启动");
        } else {
            fillExecuteRes(res, false, true, "任务已在运行中");
        }
        return res;
    }

    @Override
    public MonitorTaskExecuteRes stopById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        boolean stopped = syntheticTaskRuntimeManager.stop(id);
        preparedContextMap.remove(id);

        SyntheticTaskEntity update = new SyntheticTaskEntity();
        update.setId(id);
        update.setRunning(0);
        syntheticTaskService.updateById(update);

        fillExecuteRes(res, stopped, false, stopped ? "任务已停止" : "任务未在运行");
        return res;
    }

    private void executeTaskLoop(Long taskId) {
        if (shouldStop(taskId)) {
            return;
        }

        SyntheticTaskEntity task = syntheticTaskService.getById(taskId);
        if (task == null) {
            stopTask(taskId, "任务不存在，已自动停止");
            return;
        }
        if (task.getStatus() == null || task.getStatus() != 1) {
            stopTask(taskId, "任务已禁用，自动停止");
            return;
        }
        if (isTargetReached(task)) {
            stopTask(taskId, "已达到最大成功次数，自动停止");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (task.getExecuteStartTime() == null) {
            updateLastResult(taskId, "NO_START_TIME", "未配置开始时间");
            sleep(1000L);
            return;
        }

        long millisUntilExecute = Duration.between(now, task.getExecuteStartTime()).toMillis();
        if (millisUntilExecute > PREPARE_AHEAD_MILLIS) {
            sleep(Math.min(millisUntilExecute - PREPARE_AHEAD_MILLIS, 1000L));
            return;
        }

        prepareExecutionContextIfNeeded(task);

        if (LocalDateTime.now().isBefore(task.getExecuteStartTime())) {
            long remainMillis = Duration.between(LocalDateTime.now(), task.getExecuteStartTime()).toMillis();
            sleep(Math.min(Math.max(remainMillis, 5L), 50L));
            return;
        }

        firePreparedOrFallback(task);
        sleep(normalizeInterval(task.getIntervalMillis()));
    }

    private void prepareExecutionContextIfNeeded(SyntheticTaskEntity task) {
        PreparedExecutionContext context = preparedContextMap.get(task.getId());
        if (context != null && !context.isExpired()) {
            return;
        }
        PreparedExecutionContext prepared = prepareExecutionContext(task);
        if (prepared != null) {
            preparedContextMap.put(task.getId(), prepared);
        }
    }

    private PreparedExecutionContext prepareExecutionContext(SyntheticTaskEntity task) {
        IboxAccountEntity account = getTaskAccount(task);
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            updateLastResult(task.getId(), "ACCOUNT_INVALID", "未找到可用账号或账号 token 为空");
            return null;
        }

        log.info("【活动接口】开始调用 center detail(预热阶段), taskId:{}, syntheticId:{}, accountId:{}",
            task.getId(), task.getSyntheticId(), account.getId());
        IboxApiResponse<SyntheticCenterRes> centerResponse = iboxClient.get(
            account.getBearerToken(),
            String.format(UrlConstant.SYNTHETIC_CENTER_DETAIL_URL, task.getSyntheticId()),
            null,
            SyntheticCenterRes.class
        );
        log.info("【活动接口】center detail(预热阶段) 调用结束, success:{}, taskId:{}, syntheticId:{}, accountId:{}",
            isSuccess(centerResponse), task.getId(), task.getSyntheticId(), account.getId());
        if (!isSuccess(centerResponse) || centerResponse.getData() == null) {
            updateLastResult(task.getId(), "PREPARE_CENTER_FAIL", "预热阶段获取合成中心详情失败");
            return null;
        }

        SyntheticCenterRes center = centerResponse.getData();
        if (center.getSyntheticStatus() == null || center.getSyntheticStatus() != 1) {
            updateLastResult(task.getId(), "PREPARE_CENTER_CLOSED", "预热阶段发现当前通道不可合成");
            return null;
        }
        if (center.getEndTime() != null && LocalDateTime.now().isAfter(ofMillis(center.getEndTime()))) {
            updateLastResult(task.getId(), "PREPARE_CENTER_ENDED", "预热阶段发现活动已结束");
            return null;
        }

        List<Long> materialIds = resolveMaterialAlbumIds(task, center);
        if (CollectionUtils.isEmpty(materialIds)) {
            updateLastResult(task.getId(), "PREPARE_MATERIAL_EMPTY", "预热阶段未匹配到可用材料");
            return null;
        }

        SyntheticConfirmReq confirmReq = new SyntheticConfirmReq();
        confirmReq.setSyntheticId(task.getSyntheticId());
        confirmReq.setSyntheticNum(1);
        confirmReq.setPreferentialAlbumIds(materialIds);

        updateLastResult(task.getId(), "PREPARED", "预热完成，等待执行");
        return new PreparedExecutionContext(task.getId(), account.getId(), account, center, materialIds, confirmReq, System.currentTimeMillis());
    }

    private void firePreparedOrFallback(SyntheticTaskEntity task) {
        PreparedExecutionContext context = preparedContextMap.get(task.getId());
        if (context == null || context.isExpired()) {
            executeOnceFallback(task);
            return;
        }

        Long currentAccountId = extractSingleAccountId(task);
        if (currentAccountId == null || !Objects.equals(currentAccountId, context.getAccountId())) {
            executeOnceFallback(task);
            return;
        }

        IboxAccountEntity account = context.getAccount();
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            executeOnceFallback(task);
            return;
        }

        log.info("【活动接口】开始调用 confirm(缓存直发), taskId:{}, syntheticId:{}, accountId:{}",
            task.getId(), task.getSyntheticId(), account.getId());
        IboxApiResponse<IboxPageDataRes<SyntheticConfirmResultRes>> confirmResponse = iboxClient.post(
            account.getBearerToken(),
            String.format(UrlConstant.SYNTHETIC_CONFIRM_URL, account.getIboxUid()),
            context.getConfirmReq(),
            new TypeReference<>() {
            }
        );
        log.info("【活动接口】confirm(缓存直发) 调用结束, success:{}, taskId:{}, syntheticId:{}, accountId:{}",
            isSuccess(confirmResponse), task.getId(), task.getSyntheticId(), account.getId());
        if (!isSuccess(confirmResponse)) {
            String message = confirmResponse == null ? "合成接口未返回有效响应" : confirmResponse.getMessage();
            saveRecord(task, account, context.getConfirmReq(), confirmResponse, "CONFIRM_FAIL", defaultMessage(message, "合成失败"), false, null);

            // 保留现有测试逻辑：失败分支仍然模拟累加成功次数。
            int successCount = (task.getSuccessCount() == null ? 0 : task.getSuccessCount()) + 1;
            SyntheticTaskEntity update = new SyntheticTaskEntity();
            update.setId(task.getId());
            update.setSuccessCount(successCount);
            update.setLastExecuteTime(LocalDateTime.now());
            update.setLastResultCode("CONFIRM_FAIL");
            update.setLastResultMessage(defaultMessage(message, "合成失败"));
            syntheticTaskService.updateById(update);
            if (isTargetReached(task.getMaxSyntheticCount(), successCount)) {
                stopTask(task.getId(), "已达到最大成功次数，自动停止");
            }
            return;
        }

        List<Long> tokenIds = extractTokenIds(confirmResponse.getData() == null ? null : confirmResponse.getData().getList());
        int successCount = (task.getSuccessCount() == null ? 0 : task.getSuccessCount()) + 1;

        SyntheticTaskEntity update = new SyntheticTaskEntity();
        update.setId(task.getId());
        update.setSuccessCount(successCount);
        update.setLastExecuteTime(LocalDateTime.now());
        update.setLastResultCode("SUCCESS");
        update.setLastResultMessage("合成成功");
        syntheticTaskService.updateById(update);

        saveRecord(task, account, context.getConfirmReq(), confirmResponse, "SUCCESS", "合成成功", true, tokenIds);
        sendSuccessFeishuMessage(task, account, successCount, tokenIds);

        if (isTargetReached(task.getMaxSyntheticCount(), successCount)) {
            stopTask(task.getId(), "已达到最大成功次数，自动停止");
        }
    }

    private void executeOnceFallback(SyntheticTaskEntity task) {
        PreparedExecutionContext context = preparedContextMap.get(task.getId());
        Long currentAccountId = extractSingleAccountId(task);
        if (context != null
            && !context.isExpired()
            && currentAccountId != null
            && Objects.equals(currentAccountId, context.getAccountId())) {
            IboxAccountEntity cachedAccount = context.getAccount();
            if (cachedAccount != null && StringUtils.hasText(cachedAccount.getBearerToken())) {
                log.info("【活动接口】开始调用 confirm(缓存兜底), taskId:{}, syntheticId:{}, accountId:{}",
                    task.getId(), task.getSyntheticId(), cachedAccount.getId());
                IboxApiResponse<IboxPageDataRes<SyntheticConfirmResultRes>> confirmResponse = iboxClient.post(
                    cachedAccount.getBearerToken(),
                    String.format(UrlConstant.SYNTHETIC_CONFIRM_URL, cachedAccount.getIboxUid()),
                    context.getConfirmReq(),
                    new TypeReference<>() {
                    }
                );
                log.info("【活动接口】confirm(缓存兜底) 调用结束, success:{}, taskId:{}, syntheticId:{}, accountId:{}",
                    isSuccess(confirmResponse), task.getId(), task.getSyntheticId(), cachedAccount.getId());
                if (isSuccess(confirmResponse)) {
                    List<Long> tokenIds = extractTokenIds(confirmResponse.getData() == null ? null : confirmResponse.getData().getList());
                    int successCount = (task.getSuccessCount() == null ? 0 : task.getSuccessCount()) + 1;

                    SyntheticTaskEntity update = new SyntheticTaskEntity();
                    update.setId(task.getId());
                    update.setSuccessCount(successCount);
                    update.setLastExecuteTime(LocalDateTime.now());
                    update.setLastResultCode("SUCCESS");
                    update.setLastResultMessage("合成成功");
                    syntheticTaskService.updateById(update);

                    saveRecord(task, cachedAccount, context.getConfirmReq(), confirmResponse, "SUCCESS", "合成成功", true, tokenIds);
                    sendSuccessFeishuMessage(task, cachedAccount, successCount, tokenIds);

                    if (isTargetReached(task.getMaxSyntheticCount(), successCount)) {
                        stopTask(task.getId(), "已达到最大成功次数，自动停止");
                    }
                    return;
                }
            }
        }

        IboxAccountEntity account = getTaskAccount(task);
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            updateLastResult(task.getId(), "ACCOUNT_INVALID", "未找到可用账号或账号 token 为空");
            return;
        }

        log.info("【活动接口】开始调用 center detail(实时兜底), taskId:{}, syntheticId:{}, accountId:{}",
            task.getId(), task.getSyntheticId(), account.getId());
        IboxApiResponse<SyntheticCenterRes> centerResponse = iboxClient.get(
            account.getBearerToken(),
            String.format(UrlConstant.SYNTHETIC_CENTER_DETAIL_URL, task.getSyntheticId()),
            null,
            SyntheticCenterRes.class
        );
        log.info("【活动接口】center detail(实时兜底) 调用结束, success:{}, taskId:{}, syntheticId:{}, accountId:{}",
            isSuccess(centerResponse), task.getId(), task.getSyntheticId(), account.getId());
        if (!isSuccess(centerResponse) || centerResponse.getData() == null) {
            saveRecord(task, account, null, centerResponse, "CENTER_FAIL", "获取合成中心详情失败", false, null);
            updateLastResult(task.getId(), "CENTER_FAIL", "获取合成中心详情失败");
            return;
        }

        SyntheticCenterRes center = centerResponse.getData();
        if (center.getSyntheticStatus() == null || center.getSyntheticStatus() != 1) {
            saveRecord(task, account, null, centerResponse, "CENTER_CLOSED", "当前通道不可合成", false, null);
            stopTask(task.getId(), "当前通道不可合成，自动停止");
            return;
        }
        if (center.getEndTime() != null && LocalDateTime.now().isAfter(ofMillis(center.getEndTime()))) {
            saveRecord(task, account, null, centerResponse, "CENTER_ENDED", "活动已结束", false, null);
            stopTask(task.getId(), "活动已结束，自动停止");
            return;
        }

        List<Long> materialIds = resolveMaterialAlbumIds(task, center);
        if (CollectionUtils.isEmpty(materialIds)) {
            saveRecord(task, account, null, centerResponse, "MATERIAL_EMPTY", "未匹配到可用材料", false, null);
            updateLastResult(task.getId(), "MATERIAL_EMPTY", "未匹配到可用材料");
            return;
        }

        SyntheticConfirmReq req = new SyntheticConfirmReq();
        req.setSyntheticId(task.getSyntheticId());
        req.setSyntheticNum(1);
        req.setPreferentialAlbumIds(materialIds);

        log.info("【活动接口】开始调用 confirm(实时兜底), taskId:{}, syntheticId:{}, accountId:{}",
            task.getId(), task.getSyntheticId(), account.getId());
        IboxApiResponse<IboxPageDataRes<SyntheticConfirmResultRes>> confirmResponse = iboxClient.post(
            account.getBearerToken(),
            String.format(UrlConstant.SYNTHETIC_CONFIRM_URL, account.getIboxUid()),
            req,
            new TypeReference<>() {
            }
        );
        log.info("【活动接口】confirm(实时兜底) 调用结束, success:{}, taskId:{}, syntheticId:{}, accountId:{}",
            isSuccess(confirmResponse), task.getId(), task.getSyntheticId(), account.getId());
        if (!isSuccess(confirmResponse)) {
            String message = confirmResponse == null ? "合成接口未返回有效响应" : confirmResponse.getMessage();
            saveRecord(task, account, req, confirmResponse, "CONFIRM_FAIL", defaultMessage(message, "合成失败"), false, null);

            // 保留现有测试逻辑：失败分支仍然模拟累加成功次数。
            int successCount = (task.getSuccessCount() == null ? 0 : task.getSuccessCount()) + 1;
            SyntheticTaskEntity update = new SyntheticTaskEntity();
            update.setId(task.getId());
            update.setSuccessCount(successCount);
            update.setLastExecuteTime(LocalDateTime.now());
            update.setLastResultCode("CONFIRM_FAIL");
            update.setLastResultMessage(defaultMessage(message, "合成失败"));
            syntheticTaskService.updateById(update);
            if (isTargetReached(task.getMaxSyntheticCount(), successCount)) {
                stopTask(task.getId(), "已达到最大成功次数，自动停止");
            }
            return;
        }

        List<Long> tokenIds = extractTokenIds(confirmResponse.getData() == null ? null : confirmResponse.getData().getList());
        int successCount = (task.getSuccessCount() == null ? 0 : task.getSuccessCount()) + 1;

        SyntheticTaskEntity update = new SyntheticTaskEntity();
        update.setId(task.getId());
        update.setSuccessCount(successCount);
        update.setLastExecuteTime(LocalDateTime.now());
        update.setLastResultCode("SUCCESS");
        update.setLastResultMessage("合成成功");
        syntheticTaskService.updateById(update);

        saveRecord(task, account, req, confirmResponse, "SUCCESS", "合成成功", true, tokenIds);
        sendSuccessFeishuMessage(task, account, successCount, tokenIds);

        if (isTargetReached(task.getMaxSyntheticCount(), successCount)) {
            stopTask(task.getId(), "已达到最大成功次数，自动停止");
        }
    }

    private List<Long> resolveMaterialAlbumIds(SyntheticTaskEntity task, SyntheticCenterRes center) {
        List<Long> manualIds = parseLongValues(task.getMaterialAlbumIds());
        if (MATERIAL_STRATEGY_MANUAL.equalsIgnoreCase(normalizeMaterialStrategy(task.getMaterialStrategy()))
            && !CollectionUtils.isEmpty(manualIds)) {
            return manualIds;
        }
        if (CollectionUtils.isEmpty(center.getBurnAlbums())) {
            return Collections.emptyList();
        }

        List<Long> result = new ArrayList<>();
        for (SyntheticBurnAlbumGroupRes group : center.getBurnAlbums()) {
            if (CollectionUtils.isEmpty(group.getAlbums())) {
                continue;
            }
            SyntheticAlbumRes matchedAlbum = null;
            for (SyntheticAlbumRes album : group.getAlbums()) {
                // 保留现有测试逻辑：自动匹配只要 album != null 就命中。
                if (album != null) {
                    matchedAlbum = album;
                    break;
                }
            }
            if (matchedAlbum == null) {
                return Collections.emptyList();
            }
            result.add(matchedAlbum.getId());
        }
        return result;
    }

    private void saveRecord(SyntheticTaskEntity task, IboxAccountEntity account, Object requestBody, Object responseBody,
                            String resultCode, String resultMessage, boolean success, List<Long> tokenIds) {
        SyntheticTaskRecordEntity record = new SyntheticTaskRecordEntity();
        record.setTaskId(task.getId());
        record.setAccountId(account == null ? null : account.getId());
        record.setActivityId(task.getActivityId());
        record.setSyntheticId(task.getSyntheticId());
        record.setRequestPayload(requestBody == null ? null : JSONObject.toJSONString(requestBody));
        record.setResponsePayload(responseBody == null ? null : JSONObject.toJSONString(responseBody));
        record.setResultCode(resultCode);
        record.setResultMessage(resultMessage);
        record.setSuccessFlag(success ? 1 : 0);
        record.setTargetTokenIds(joinLongValues(tokenIds));
        syntheticTaskRecordService.save(record);
    }

    private void validateTask(String taskName, Long activityId, Long syntheticId, List<Long> accountIds,
                              LocalDateTime executeStartTime, LocalDateTime executeEndTime,
                              Integer maxSyntheticCount, Long intervalMillis) {
        if (!StringUtils.hasText(taskName)) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (activityId == null) {
            throw new IllegalArgumentException("活动 ID 不能为空");
        }
        if (syntheticId == null) {
            throw new IllegalArgumentException("合成通道 ID 不能为空");
        }
        if (CollectionUtils.isEmpty(accountIds)) {
            throw new IllegalArgumentException("至少选择一个账号");
        }
        if (accountIds.size() != 1) {
            throw new IllegalArgumentException("活动合成任务只允许选择一个账号");
        }
        if (executeStartTime == null) {
            throw new IllegalArgumentException("开始时间不能为空");
        }
        if (executeEndTime != null && !executeStartTime.isBefore(executeEndTime)) {
            throw new IllegalArgumentException("开始时间必须早于结束时间");
        }
        if (maxSyntheticCount == null || maxSyntheticCount < 1) {
            throw new IllegalArgumentException("最大成功次数必须大于 0");
        }
        if (intervalMillis == null || intervalMillis < 50L) {
            throw new IllegalArgumentException("执行间隔不能小于 50 毫秒");
        }
    }

    private void applyDefaults(SyntheticTaskSaveReq req) {
        if (!StringUtils.hasText(req.getTaskName())) {
            req.setTaskName(generateRandomTaskCode());
        }
        if (req.getMaxSyntheticCount() == null || req.getMaxSyntheticCount() <= 0) {
            req.setMaxSyntheticCount(5);
        }
    }

    private void applyDefaults(SyntheticTaskUpdateReq req) {
        if (!StringUtils.hasText(req.getTaskName())) {
            req.setTaskName(generateRandomTaskCode());
        }
        if (req.getMaxSyntheticCount() == null || req.getMaxSyntheticCount() <= 0) {
            req.setMaxSyntheticCount(5);
        }
    }

    private String generateRandomTaskCode() {
        StringBuilder builder = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            builder.append(ThreadLocalRandom.current().nextInt(10));
        }
        return builder.toString();
    }

    /**
     * 创建或编辑任务后立即尝试预热。
     *
     * <p>这样即使活动开始时 center/detail 接口挂掉，
     * 只要这里成功预热过一次，运行时仍然可以优先走缓存直接 confirm。</p>
     */
    private void prepareContextAfterMutation(Long taskId) {
        if (taskId == null) {
            return;
        }
        SyntheticTaskEntity latest = syntheticTaskService.getById(taskId);
        if (latest == null) {
            return;
        }
        PreparedExecutionContext prepared = prepareExecutionContext(latest);
        if (prepared != null) {
            preparedContextMap.put(taskId, prepared);
        }
    }

    private void updateLastResult(Long taskId, String code, String message) {
        SyntheticTaskEntity update = new SyntheticTaskEntity();
        update.setId(taskId);
        update.setLastExecuteTime(LocalDateTime.now());
        update.setLastResultCode(code);
        update.setLastResultMessage(message);
        syntheticTaskService.updateById(update);
    }

    private void stopTask(Long taskId, String message) {
        SyntheticTaskEntity update = new SyntheticTaskEntity();
        update.setId(taskId);
        update.setRunning(0);
        update.setLastExecuteTime(LocalDateTime.now());
        update.setLastResultMessage(message);
        syntheticTaskService.updateById(update);
        preparedContextMap.remove(taskId);
        syntheticTaskRuntimeManager.stop(taskId);
    }

    private void fillExecuteRes(MonitorTaskExecuteRes res, boolean success, boolean running, String message) {
        res.setSuccess(success);
        res.setRunning(running);
        res.setMessage(message);
        res.setEndTime(LocalDateTime.now());
    }

    private boolean shouldStop(Long taskId) {
        return Thread.currentThread().isInterrupted() || !syntheticTaskRuntimeManager.isRunning(taskId);
    }

    private boolean isTargetReached(SyntheticTaskEntity task) {
        return isTargetReached(task.getMaxSyntheticCount(), task.getSuccessCount() == null ? 0 : task.getSuccessCount());
    }

    private boolean isTargetReached(Integer maxCount, int successCount) {
        return maxCount != null && maxCount > 0 && successCount >= maxCount;
    }

    private long normalizeInterval(Long intervalMillis) {
        return intervalMillis == null || intervalMillis <= 0 ? 1000L : intervalMillis;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(millis, 0L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private IboxAccountEntity getTaskAccount(SyntheticTaskEntity task) {
        Long accountId = extractSingleAccountId(task);
        return accountId == null ? null : getAccount(accountId);
    }

    private Long extractSingleAccountId(SyntheticTaskEntity task) {
        List<Long> accountIds = parseLongValues(task.getAccountIds());
        if (CollectionUtils.isEmpty(accountIds)) {
            return null;
        }
        return accountIds.getFirst();
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

    private SyntheticTaskRes buildTaskRes(SyntheticTaskEntity entity) {
        SyntheticTaskRes res = new SyntheticTaskRes();
        BeanUtils.copyProperties(entity, res);
        List<Long> accountIds = parseLongValues(entity.getAccountIds());
        res.setAccountIds(accountIds);
        res.setAccountNames(buildAccountNames(accountIds));
        res.setMaterialAlbumIds(parseLongValues(entity.getMaterialAlbumIds()));
        return res;
    }

    private SyntheticTaskRecordRes buildRecordRes(SyntheticTaskRecordEntity entity) {
        SyntheticTaskRecordRes res = new SyntheticTaskRecordRes();
        BeanUtils.copyProperties(entity, res);
        IboxAccountEntity account = getAccount(entity.getAccountId());
        if (account != null) {
            res.setAccountName(account.getAccountName());
        }
        return res;
    }

    private List<String> buildAccountNames(List<Long> accountIds) {
        if (CollectionUtils.isEmpty(accountIds)) {
            return Collections.emptyList();
        }
        return accountIds.stream()
            .map(this::getAccount)
            .filter(Objects::nonNull)
            .map(account -> StringUtils.hasText(account.getPhone())
                ? account.getAccountName() + "(" + account.getPhone() + ")"
                : account.getAccountName() + "(ID:" + account.getId() + ")")
            .toList();
    }

    private List<Long> parseLongValues(String values) {
        if (!StringUtils.hasText(values)) {
            return new ArrayList<>();
        }
        return Arrays.stream(values.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .distinct()
            .map(value -> {
                try {
                    return Long.valueOf(value);
                } catch (NumberFormatException ex) {
                    log.warn("【活动合成任务】解析数值失败，value:{}", value, ex);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private String joinLongValues(List<Long> values) {
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), list -> String.join(",", list)));
    }

    private String normalizeMaterialStrategy(String materialStrategy) {
        if (!StringUtils.hasText(materialStrategy)) {
            return MATERIAL_STRATEGY_MANUAL;
        }
        if (MATERIAL_STRATEGY_AUTO_MATCH.equalsIgnoreCase(materialStrategy)) {
            return MATERIAL_STRATEGY_AUTO_MATCH;
        }
        return MATERIAL_STRATEGY_MANUAL;
    }

    private List<Long> extractTokenIds(List<SyntheticConfirmResultRes> resultList) {
        if (CollectionUtils.isEmpty(resultList)) {
            return Collections.emptyList();
        }
        List<Long> tokenIds = new ArrayList<>();
        for (SyntheticConfirmResultRes result : resultList) {
            if (!CollectionUtils.isEmpty(result.getTokenIds())) {
                tokenIds.addAll(result.getTokenIds());
            }
        }
        return tokenIds;
    }

    private LocalDateTime ofMillis(Long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    private String defaultMessage(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private void sendSuccessFeishuMessage(SyntheticTaskEntity task, IboxAccountEntity account, int successCount, List<Long> tokenIds) {
        try {
            String accountText = account == null
                ? "-"
                : (StringUtils.hasText(account.getPhone())
                ? account.getAccountName() + "(" + account.getPhone() + ")"
                : account.getAccountName() + "(ID:" + account.getId() + ")");
            String tokenText = CollectionUtils.isEmpty(tokenIds)
                ? "-"
                : tokenIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String message = "iBox活动合成成功"
                + "，活动：" + defaultMessage(task.getActivityName(), "-")
                + "，通道：" + defaultMessage(task.getChannelName(), "-")
                + "，任务ID：" + task.getId()
                + "，账号：" + accountText
                + "，成功次数：" + successCount + "/" + (task.getMaxSyntheticCount() == null ? "-" : task.getMaxSyntheticCount())
                + "，TokenIds：" + tokenText;
            feishuMessageUtil.sendText(message);
        } catch (Exception ex) {
            log.warn("【活动合成任务】发送飞书通知失败，taskId:{}, accountId:{}", task.getId(), account == null ? null : account.getId(), ex);
        }
    }

    private boolean isSuccess(IboxApiResponse<?> response) {
        return response != null && response.getCode() != null && response.getCode() == 0;
    }

    @Data
    @AllArgsConstructor
    private static class PreparedExecutionContext {
        private Long taskId;
        private Long accountId;
        private IboxAccountEntity account;
        private SyntheticCenterRes center;
        private List<Long> materialIds;
        private SyntheticConfirmReq confirmReq;
        private long preparedAtMillis;

        public boolean isExpired() {
            return System.currentTimeMillis() - preparedAtMillis > PREPARED_CONTEXT_EXPIRE_MILLIS;
        }
    }
}
