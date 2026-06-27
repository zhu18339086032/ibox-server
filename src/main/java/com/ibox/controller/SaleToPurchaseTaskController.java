package com.ibox.controller;

import com.alibaba.fastjson.JSONObject;
import com.ibox.manager.SaleToPurchaseTaskManager;
import com.ibox.model.req.web.SaleToPurchaseTaskPageReq;
import com.ibox.model.req.web.SaleToPurchaseTaskSaveReq;
import com.ibox.model.req.web.SaleToPurchaseTaskUpdateReq;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.SaleToPurchaseTaskRes;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 出售求购任务控制器
 */
@RestController
@RequestMapping("/ibox/sale-to-purchase-task")
@Slf4j
@Tag(name = "出售求购任务")
public class SaleToPurchaseTaskController {

    @Autowired
    private SaleToPurchaseTaskManager saleToPurchaseTaskManager;

    @GetMapping("/list")
    @Operation(summary = "获取出售求购任务列表")
    public WebResponse<PageResponse<SaleToPurchaseTaskRes>> list(SaleToPurchaseTaskPageReq req) {
        log.info("【出售求购任务列表】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(saleToPurchaseTaskManager.list(req));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "根据ID获取出售求购任务")
    public WebResponse<SaleToPurchaseTaskRes> getById(@PathVariable Long id) {
        log.info("【出售求购任务详情】任务ID:{}", id);
        return WebResponse.success(saleToPurchaseTaskManager.getById(id));
    }

    @PostMapping("/save")
    @Operation(summary = "保存出售求购任务")
    public WebResponse<Boolean> save(@RequestBody SaleToPurchaseTaskSaveReq req) {
        log.info("【保存出售求购任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(saleToPurchaseTaskManager.save(req));
    }

    @PostMapping("/update")
    @Operation(summary = "修改出售求购任务")
    public WebResponse<Boolean> update(@RequestBody SaleToPurchaseTaskUpdateReq req) {
        log.info("【修改出售求购任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(saleToPurchaseTaskManager.updateById(req));
    }

    @PostMapping("/remove/{id}")
    @Operation(summary = "删除出售求购任务")
    public WebResponse<Boolean> remove(@PathVariable Long id) {
        log.info("【删除出售求购任务】任务ID:{}", id);
        return WebResponse.success(saleToPurchaseTaskManager.removeById(id));
    }

    @PostMapping("/start/{id}")
    @Operation(summary = "启动出售求购任务")
    public WebResponse<MonitorTaskExecuteRes> start(@PathVariable Long id) {
        log.info("【启动出售求购任务】任务ID:{}", id);
        return WebResponse.success(saleToPurchaseTaskManager.startById(id));
    }

    @PostMapping("/stop/{id}")
    @Operation(summary = "停止出售求购任务")
    public WebResponse<MonitorTaskExecuteRes> stop(@PathVariable Long id) {
        log.info("【停止出售求购任务】任务ID:{}", id);
        return WebResponse.success(saleToPurchaseTaskManager.stopById(id));
    }
}
