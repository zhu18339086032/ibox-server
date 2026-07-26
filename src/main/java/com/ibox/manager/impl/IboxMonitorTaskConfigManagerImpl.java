package com.ibox.manager.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ibox.common.IboxAccountCache;
import com.ibox.common.MonitorTaskRuntimeManager;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.IboxMonitorTaskConfigEntity;
import com.ibox.manager.AnnouncementMonitorManager;
import com.ibox.manager.IboxMonitorTaskConfigManager;
import com.ibox.model.req.web.IboxMonitorTaskConfigPageReq;
import com.ibox.model.req.web.IboxMonitorTaskConfigSaveReq;
import com.ibox.model.req.web.IboxMonitorTaskConfigUpdateReq;
import com.ibox.model.res.web.IboxMonitorTaskConfigRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.service.IboxAccountService;
import com.ibox.service.IboxMonitorTaskConfigService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class IboxMonitorTaskConfigManagerImpl implements IboxMonitorTaskConfigManager {

    @Autowired
    private IboxMonitorTaskConfigService iboxMonitorTaskConfigService;

    @Autowired
    private AnnouncementMonitorManager announcementMonitorManager;

    @Autowired
    private MonitorTaskRuntimeManager monitorTaskRuntimeManager;

    @Autowired
    private IboxAccountService iboxAccountService;

    @Autowired
    private IboxAccountCache iboxAccountCache;

    @Override
    public PageResponse<IboxMonitorTaskConfigRes> list(IboxMonitorTaskConfigPageReq req) {
        LambdaQueryWrapper<IboxMonitorTaskConfigEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(req.getTaskName()), IboxMonitorTaskConfigEntity::getTaskName, req.getTaskName());
        queryWrapper.eq(StringUtils.hasText(req.getMonitorType()), IboxMonitorTaskConfigEntity::getMonitorType, req.getMonitorType());
        queryWrapper.apply(req.getAccountId() != null, "FIND_IN_SET({0}, account_ids)", req.getAccountId());
        queryWrapper.eq(req.getStatus() != null, IboxMonitorTaskConfigEntity::getStatus, req.getStatus());
        queryWrapper.orderByDesc(IboxMonitorTaskConfigEntity::getId);
        IPage<IboxMonitorTaskConfigEntity> page = iboxMonitorTaskConfigService.page(new Page<>(req.getPageNum(), req.getPageSize()), queryWrapper);
        List<IboxMonitorTaskConfigRes> records = page.getRecords().stream().map(this::buildRes).toList();
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public IboxMonitorTaskConfigRes getById(Long id) {
        IboxMonitorTaskConfigEntity entity = iboxMonitorTaskConfigService.getById(id);
        return entity == null ? null : buildRes(entity);
    }

    @Override
    public boolean save(IboxMonitorTaskConfigSaveReq req) {
        IboxMonitorTaskConfigEntity entity = new IboxMonitorTaskConfigEntity();
        BeanUtils.copyProperties(req, entity, "accountIds");
        entity.setAccountIds(joinAccountIds(req.getAccountIds()));
        return iboxMonitorTaskConfigService.save(entity);
    }

    @Override
    public boolean updateById(IboxMonitorTaskConfigUpdateReq req) {
        IboxMonitorTaskConfigEntity entity = new IboxMonitorTaskConfigEntity();
        BeanUtils.copyProperties(req, entity, "accountIds");
        entity.setAccountIds(joinAccountIds(req.getAccountIds()));
        return iboxMonitorTaskConfigService.updateById(entity);
    }

    @Override
    public boolean removeById(Long id) {
        return iboxMonitorTaskConfigService.removeById(id);
    }

    @Override
    public MonitorTaskExecuteRes executeById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        IboxMonitorTaskConfigEntity entity = iboxMonitorTaskConfigService.getById(id);
        if (entity == null) {
            res.setSuccess(false);
            res.setMessage("监控任务配置不存在");
            res.setEndTime(LocalDateTime.now());
            return res;
        }

        res.setMonitorType(entity.getMonitorType());
        if (entity.getStatus() == null || entity.getStatus() != 1) {
            res.setSuccess(false);
            res.setRunning(false);
            res.setMessage("监控任务未启用");
            res.setEndTime(LocalDateTime.now());
            return res;
        }

        try {
            List<IboxAccountEntity> accounts = resolveMonitorAccounts(entity);
            boolean started = monitorTaskRuntimeManager.start(entity.getId(), () -> {
                try {
                    dispatchExecute(entity, accounts);
                } catch (Exception ignored) {
                }
            });
            if (!started) {
                res.setSuccess(false);
                res.setRunning(true);
                res.setMessage("监控任务已在后台运行");
            } else {
                res.setSuccess(true);
                res.setRunning(true);
                res.setMessage("监控任务已启动并在后台持续执行");
            }
        } catch (Exception e) {
            res.setSuccess(false);
            res.setRunning(false);
            res.setMessage(e.getMessage());
        }
        res.setEndTime(LocalDateTime.now());
        return res;
    }

    @Override
    public MonitorTaskExecuteRes stopById(Long id) {
        MonitorTaskExecuteRes res = new MonitorTaskExecuteRes();
        res.setTaskId(id);
        res.setStartTime(LocalDateTime.now());

        IboxMonitorTaskConfigEntity entity = iboxMonitorTaskConfigService.getById(id);
        if (entity != null) {
            res.setMonitorType(entity.getMonitorType());
            if ("RECOMMEND_AREA".equalsIgnoreCase(entity.getMonitorType())) {
                announcementMonitorManager.clearRecommendAreaState(id);
            }
        }

        boolean stopped = monitorTaskRuntimeManager.stop(id);
        res.setSuccess(stopped);
        res.setRunning(false);
        res.setMessage(stopped ? "监控任务已停止" : "监控任务未在运行");
        res.setEndTime(LocalDateTime.now());
        return res;
    }

    private IboxMonitorTaskConfigRes buildRes(IboxMonitorTaskConfigEntity entity) {
        IboxMonitorTaskConfigRes res = new IboxMonitorTaskConfigRes();
        BeanUtils.copyProperties(entity, res, "accountIds");
        List<Long> accountIds = parseAccountIds(entity.getAccountIds());
        res.setAccountIds(accountIds);
        res.setAccountNames(buildAccountNames(accountIds));
        res.setRunning(monitorTaskRuntimeManager.isRunning(entity.getId()) ? 1 : 0);
        return res;
    }

    private void dispatchExecute(IboxMonitorTaskConfigEntity entity, List<IboxAccountEntity> accounts) {
        if ("ANNOUNCEMENT".equalsIgnoreCase(entity.getMonitorType())) {
            announcementMonitorManager.monitorLatestAnnouncement(entity, accounts);
            return;
        }
        if ("RECOMMEND_AREA".equalsIgnoreCase(entity.getMonitorType())) {
            announcementMonitorManager.monitorRecommendArea(entity, accounts);
            return;
        }
        throw new IllegalStateException("暂不支持的监控类型: " + entity.getMonitorType());
    }

    private String joinAccountIds(List<Long> accountIds) {
        if (CollectionUtils.isEmpty(accountIds)) {
            return null;
        }
        return accountIds.stream()
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), values -> String.join(",", values)));
    }

    private List<Long> parseAccountIds(String accountIds) {
        if (!StringUtils.hasText(accountIds)) {
            return List.of();
        }
        return Arrays.stream(accountIds.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .distinct()
            .map(value -> {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private List<String> buildAccountNames(List<Long> accountIds) {
        if (CollectionUtils.isEmpty(accountIds)) {
            return List.of();
        }
        return accountIds.stream()
            .map(this::getAccount)
            .filter(Objects::nonNull)
            .map(account -> StringUtils.hasText(account.getPhone())
                ? account.getAccountName() + "(" + account.getPhone() + ")"
                : account.getAccountName() + "(ID:" + account.getId() + ")")
            .toList();
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

    private List<IboxAccountEntity> resolveMonitorAccounts(IboxMonitorTaskConfigEntity entity) {
        List<Long> accountIds = parseAccountIds(entity == null ? null : entity.getAccountIds());
        if (CollectionUtils.isEmpty(accountIds)) {
            IboxAccountEntity defaultAccount = iboxAccountCache.getDefaultAccount();
            return defaultAccount == null ? List.of() : List.of(defaultAccount);
        }
        return accountIds.stream()
            .map(this::getAccount)
            .filter(Objects::nonNull)
            .toList();
    }
}
