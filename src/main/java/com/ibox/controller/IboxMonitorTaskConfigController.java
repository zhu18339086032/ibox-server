package com.ibox.controller;

import com.alibaba.fastjson.JSONObject;
import com.ibox.manager.IboxMonitorTaskConfigManager;
import com.ibox.model.req.web.IboxMonitorTaskConfigPageReq;
import com.ibox.model.req.web.IboxMonitorTaskConfigSaveReq;
import com.ibox.model.req.web.IboxMonitorTaskConfigUpdateReq;
import com.ibox.model.res.web.IboxMonitorTaskConfigRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 监控任务配置控制器
 */
@RestController
@RequestMapping("/ibox/monitor-task-config")
@Slf4j
@Tag(name = "监控任务配置")
public class IboxMonitorTaskConfigController {

    /**
     * 监控任务配置服务
     */
    @Autowired
    private IboxMonitorTaskConfigManager iboxMonitorTaskConfigManager;

    /**
     * 获取监控任务配置列表
     *
     * @param req 查询条件
     * @return 监控任务配置分页列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取监控任务配置列表")
    public WebResponse<PageResponse<IboxMonitorTaskConfigRes>> list(IboxMonitorTaskConfigPageReq req) {
        log.info("【监控任务配置列表】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(iboxMonitorTaskConfigManager.list(req));
    }

    /**
     * 根据ID获取监控任务配置
     *
     * @param id 主键ID
     * @return 监控任务配置
     */
    @GetMapping("/get/{id}")
    @Operation(summary = "根据ID获取监控任务配置")
    public WebResponse<IboxMonitorTaskConfigRes> getById(@PathVariable Long id) {
        log.info("【监控任务配置详情】任务ID:{}", id);
        return WebResponse.success(iboxMonitorTaskConfigManager.getById(id));
    }

    /**
     * 保存监控任务配置
     *
     * @param req 保存请求
     * @return 是否成功
     */
    @PostMapping("/save")
    @Operation(summary = "保存监控任务配置")
    public WebResponse<Boolean> save(@RequestBody IboxMonitorTaskConfigSaveReq req) {
        log.info("【保存监控任务配置】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(iboxMonitorTaskConfigManager.save(req));
    }

    /**
     * 修改监控任务配置
     *
     * @param req 修改请求
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改监控任务配置")
    public WebResponse<Boolean> update(@RequestBody IboxMonitorTaskConfigUpdateReq req) {
        log.info("【修改监控任务配置】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(iboxMonitorTaskConfigManager.updateById(req));
    }

    /**
     * 删除监控任务配置
     *
     * @param id 主键ID
     * @return 是否成功
     */
    @PostMapping("/remove/{id}")
    @Operation(summary = "删除监控任务配置")
    public WebResponse<Boolean> remove(@PathVariable Long id) {
        log.info("【删除监控任务配置】任务ID:{}", id);
        return WebResponse.success(iboxMonitorTaskConfigManager.removeById(id));
    }

    /**
     * 启动监控任务
     *
     * @param id 主键ID
     * @return 执行结果
     */
    @PostMapping("/execute/{id}")
    @Operation(summary = "启动监控任务")
    public WebResponse<MonitorTaskExecuteRes> execute(@PathVariable Long id) {
        log.info("【启动监控任务】任务ID:{}", id);
        return WebResponse.success(iboxMonitorTaskConfigManager.executeById(id));
    }

    /**
     * 停止监控任务
     *
     * @param id 主键ID
     * @return 执行结果
     */
    @PostMapping("/stop/{id}")
    @Operation(summary = "停止监控任务")
    public WebResponse<MonitorTaskExecuteRes> stop(@PathVariable Long id) {
        log.info("【停止监控任务】任务ID:{}", id);
        return WebResponse.success(iboxMonitorTaskConfigManager.stopById(id));
    }
}
