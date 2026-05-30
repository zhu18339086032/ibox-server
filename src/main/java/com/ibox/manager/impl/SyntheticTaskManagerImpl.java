package com.ibox.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.common.IboxAccountCache;
import com.ibox.common.SyntheticTaskRuntimeManager;
import com.ibox.constants.TokenConstant;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 活动合成任务管理器实现。
 *
 * <p>本版重点优化执行速度：</p>
 * <p>1. 提前查询 center detail，避免到点前再查活动上下文。</p>
 * <p>2. 提前选择材料，避免到点时再做材料匹配。</p>
 * <p>3. 提前组装 confirm 请求体，到点直接调用最终合成接口。</p>
 *
 * <p>考虑到当前环境仍在联调，这里保留兜底逻辑：</p>
 * <p>如果预热失败，则自动回退到即时执行链路，确保功能不中断。</p>
 */
@Slf4j
@Service
public class SyntheticTaskManagerImpl implements SyntheticTaskManager {

    private static final String MATERIAL_STRATEGY_MANUAL = "MANUAL";
    private static final String MATERIAL_STRATEGY_AUTO_MATCH = "AUTO_MATCH";

    /**
     * 进入预热窗口后，提前多久开始准备执行上下文。
     *
     * <p>30 秒是一个偏保守的默认值，足够覆盖一次 center detail 查询、
     * 材料匹配、请求体组装和连接热身。</p>
     */
    private static final long PREPARE_AHEAD_MILLIS = 30_000L;

    /**
     * 预热结果的有效期。
     *
     * <p>超过这个时间仍未发射，则认为预热信息可能已过旧，需要重新准备。</p>
     */
    private static final long PREPARED_CONTEXT_EXPIRE_MILLIS = 60_000L;

    /**
     * 每个任务维护一个账号轮转下标，避免总是同一个账号先执行。
     */
    private final ConcurrentHashMap<Long, AtomicInteger> taskAccountIndexMap = new ConcurrentHashMap<>();

    /**
     * 每个任务维护一份预热好的执行上下文。
     *
     * <p>一旦进入预热窗口，我们就尽量把耗时动作前移，
     * 到时间时只从这里取出最终请求体直接打 confirm。</p>
     */
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
        return syntheticTaskService.save(entity);
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

        PreparedExecutionContext oldContext = preparedContextMap.remove(req.getId());
        if (oldContext != null) {
            log.info("【活动合成任务】任务配置已变更，清空旧的预热上下文，taskId:{}", req.getId());
        }

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
        return syntheticTaskService.updateById(entity);
    }

    @Override
    public boolean removeById(Long id) {
        syntheticTaskRuntimeManager.stop(id);
        taskAccountIndexMap.remove(id);
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
        if (CollectionUtils.isEmpty(parseLongValues(task.getAccountIds()))) {
            fillExecuteRes(res, false, false, "任务未配置可用账号");
            return res;
        }
        if (isTargetReached(task)) {
            fillExecuteRes(res, false, false, "任务已达到成功上限");
            return res;
        }

        taskAccountIndexMap.put(id, new AtomicInteger(0));
        preparedContextMap.remove(id);
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
        taskAccountIndexMap.remove(id);
        preparedContextMap.remove(id);

        SyntheticTaskEntity update = new SyntheticTaskEntity();
        update.setId(id);
        update.setRunning(0);
        syntheticTaskService.updateById(update);

        fillExecuteRes(res, stopped, false, stopped ? "任务已停止" : "任务未在运行");
        return res;
    }

    /**
     * 单次循环执行入口。
     *
     * <p>执行链路拆成三段：</p>
     * <p>1. 未到预热窗口：低频等待</p>
     * <p>2. 进入预热窗口：提前准备上下文</p>
     * <p>3. 到达执行时间：直接发送 confirm</p>
     */
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
            // 离执行时间还较远，不急着预热，继续低频等待。
            sleep(Math.min(millisUntilExecute - PREPARE_AHEAD_MILLIS, 1000L));
            return;
        }

        // 进入预热窗口后，尽量只预热一次；如过期则重新准备。
        prepareExecutionContextIfNeeded(task);

        if (LocalDateTime.now().isBefore(task.getExecuteStartTime())) {
            // 已完成预热，但还没到发射时间，保持高频短等待。
            long remainMillis = Duration.between(LocalDateTime.now(), task.getExecuteStartTime()).toMillis();
            sleep(Math.min(Math.max(remainMillis, 5L), 50L));
            return;
        }

        firePreparedOrFallback(task);
        sleep(normalizeInterval(task.getIntervalMillis()));
    }

    /**
     * 如果当前没有可用的预热上下文，或者上下文已过期，则重新预热。
     */
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

    /**
     * 预热执行上下文。
     *
     * <p>这里完成三件最耗时的事：</p>
     * <p>1. 查询 center detail</p>
     * <p>2. 解析材料</p>
     * <p>3. 组装最终 confirm 请求体</p>
     */
    private PreparedExecutionContext prepareExecutionContext(SyntheticTaskEntity task) {
        List<Long> accountIds = parseLongValues(task.getAccountIds());
        if (CollectionUtils.isEmpty(accountIds)) {
            updateLastResult(task.getId(), "NO_ACCOUNT", "任务未配置可用账号");
            return null;
        }

        IboxAccountEntity account = getNextAccount(task.getId(), accountIds);
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            updateLastResult(task.getId(), "ACCOUNT_INVALID", "未找到可用账号或账号 token 为空");
            return null;
        }

        IboxApiResponse<SyntheticCenterRes> centerResponse = iboxClient.get(
            TokenConstant.TOKEN_3,
            String.format(UrlConstant.SYNTHETIC_CENTER_DETAIL_URL, task.getSyntheticId()),
            null,
            SyntheticCenterRes.class
        );
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

        // 轻量热身：预热成功时更新最近状态，便于前端看到当前任务已准备就绪。
        updateLastResult(task.getId(), "PREPARED", "预热完成，等待执行");
        return new PreparedExecutionContext(task.getId(), account, center, materialIds, confirmReq, System.currentTimeMillis());
    }

    /**
     * 到点后优先使用预热上下文直接发 confirm。
     *
     * <p>如果预热不存在或已失效，则回退到原来的即时执行链路，避免任务空跑。</p>
     */
    private void firePreparedOrFallback(SyntheticTaskEntity task) {
        PreparedExecutionContext context = preparedContextMap.remove(task.getId());
        if (context == null || context.isExpired()) {
            executeOnceFallback(task);
            return;
        }

        IboxAccountEntity account = context.getAccount();
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            executeOnceFallback(task);
            return;
        }

        IboxApiResponse<IboxPageDataRes<SyntheticConfirmResultRes>> confirmResponse = iboxClient.post(
            account.getBearerToken(),
            String.format(UrlConstant.SYNTHETIC_CONFIRM_URL, account.getIboxUid()),
            context.getConfirmReq(),
            new TypeReference<>() {
            }
        );
        if (!isSuccess(confirmResponse)) {
            String message = confirmResponse == null ? "合成接口未返回有效响应" : confirmResponse.getMessage();
            saveRecord(task, account, context.getConfirmReq(), confirmResponse, "CONFIRM_FAIL", defaultMessage(message, "合成失败"), false, null);
            updateLastResult(task.getId(), "CONFIRM_FAIL", defaultMessage(message, "合成失败"));
            // 按你的测试逻辑保留：失败时也允许推进模拟成功次数。
            int successCount = (task.getSuccessCount() == null ? 0 : task.getSuccessCount()) + 1;
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

    /**
     * 兜底的即时执行链路。
     *
     * <p>当预热失败、上下文过期、账号失效时，仍沿用原来的“实时查 center -> 选材料 -> 发 confirm”流程。</p>
     */
    private void executeOnceFallback(SyntheticTaskEntity task) {
        List<Long> accountIds = parseLongValues(task.getAccountIds());
        if (CollectionUtils.isEmpty(accountIds)) {
            updateLastResult(task.getId(), "NO_ACCOUNT", "任务未配置可用账号");
            return;
        }

        IboxAccountEntity account = getNextAccount(task.getId(), accountIds);
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            updateLastResult(task.getId(), "ACCOUNT_INVALID", "未找到可用账号或账号 token 为空");
            return;
        }

        IboxApiResponse<SyntheticCenterRes> centerResponse = iboxClient.get(
            TokenConstant.TOKEN_3,
            String.format(UrlConstant.SYNTHETIC_CENTER_DETAIL_URL, task.getSyntheticId()),
            null,
            SyntheticCenterRes.class
        );
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

        IboxApiResponse<IboxPageDataRes<SyntheticConfirmResultRes>> confirmResponse = iboxClient.post(
            account.getBearerToken(),
            String.format(UrlConstant.SYNTHETIC_CONFIRM_URL, account.getIboxUid()),
            req,
            new TypeReference<>() {
            }
        );
        if (!isSuccess(confirmResponse)) {
            String message = confirmResponse == null ? "合成接口未返回有效响应" : confirmResponse.getMessage();
            saveRecord(task, account, req, confirmResponse, "CONFIRM_FAIL", defaultMessage(message, "合成失败"), false, null);
            updateLastResult(task.getId(), "CONFIRM_FAIL", defaultMessage(message, "合成失败"));
            int successCount = (task.getSuccessCount() == null ? 0 : task.getSuccessCount()) + 1;
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

    /**
     * 根据材料策略和 center 数据，决定本次要提交的材料。
     *
     * <p>这里继续保留你当前的测试逻辑：
     * 自动匹配时只要 `album != null` 就能命中，不强制要求 usableNum > 0。</p>
     */
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
        taskAccountIndexMap.remove(taskId);
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

    private IboxAccountEntity getNextAccount(Long taskId, List<Long> accountIds) {
        AtomicInteger index = taskAccountIndexMap.computeIfAbsent(taskId, key -> new AtomicInteger(0));
        int startIndex = Math.floorMod(index.getAndIncrement(), accountIds.size());
        for (int offset = 0; offset < accountIds.size(); offset++) {
            Long accountId = accountIds.get((startIndex + offset) % accountIds.size());
            IboxAccountEntity account = getAccount(accountId);
            if (account != null) {
                return account;
            }
        }
        return null;
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

    /**
     * 预热后的执行上下文。
     */
    @Data
    @AllArgsConstructor
    private static class PreparedExecutionContext {
        private Long taskId;
        private IboxAccountEntity account;
        private SyntheticCenterRes center;
        private List<Long> materialIds;
        private SyntheticConfirmReq confirmReq;
        private long preparedAtMillis;

        /**
         * 超过有效期则认为上下文已经过时，需要重新预热。
         */
        public boolean isExpired() {
            return System.currentTimeMillis() - preparedAtMillis > PREPARED_CONTEXT_EXPIRE_MILLIS;
        }
    }
}
