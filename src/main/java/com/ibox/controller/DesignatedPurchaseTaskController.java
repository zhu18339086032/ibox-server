package com.ibox.controller;

import com.alibaba.fastjson.JSONObject;
import com.ibox.manager.DesignatedPurchaseTaskManager;
import com.ibox.model.req.web.DesignatedPurchaseTaskPageReq;
import com.ibox.model.req.web.DesignatedPurchaseTaskSaveReq;
import com.ibox.model.req.web.DesignatedPurchaseTaskUpdateReq;
import com.ibox.model.res.web.DesignatedPurchaseTaskRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 指定购买任务控制器
 */
@RestController
@RequestMapping("/ibox/designated-purchase-task")
@Slf4j
@Tag(name = "指定购买任务")
public class DesignatedPurchaseTaskController {

    @Autowired
    private DesignatedPurchaseTaskManager designatedPurchaseTaskManager;

    @GetMapping("/list")
    @Operation(summary = "获取指定购买任务列表")
    public WebResponse<PageResponse<DesignatedPurchaseTaskRes>> list(DesignatedPurchaseTaskPageReq req) {
        log.info("【指定购买任务列表】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(designatedPurchaseTaskManager.list(req));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "根据ID获取指定购买任务")
    public WebResponse<DesignatedPurchaseTaskRes> getById(@PathVariable Long id) {
        log.info("【指定购买任务详情】任务ID:{}", id);
        return WebResponse.success(designatedPurchaseTaskManager.getById(id));
    }

    @PostMapping("/save")
    @Operation(summary = "保存指定购买任务")
    public WebResponse<Boolean> save(@RequestBody DesignatedPurchaseTaskSaveReq req) {
        log.info("【保存指定购买任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(designatedPurchaseTaskManager.save(req));
    }

    @PostMapping("/update")
    @Operation(summary = "修改指定购买任务")
    public WebResponse<Boolean> update(@RequestBody DesignatedPurchaseTaskUpdateReq req) {
        log.info("【修改指定购买任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(designatedPurchaseTaskManager.updateById(req));
    }

    @PostMapping("/remove/{id}")
    @Operation(summary = "删除指定购买任务")
    public WebResponse<Boolean> remove(@PathVariable Long id) {
        log.info("【删除指定购买任务】任务ID:{}", id);
        return WebResponse.success(designatedPurchaseTaskManager.removeById(id));
    }

    @PostMapping("/start/{id}")
    @Operation(summary = "启动指定购买任务")
    public WebResponse<MonitorTaskExecuteRes> start(@PathVariable Long id) {
        log.info("【启动指定购买任务】任务ID:{}", id);
        return WebResponse.success(designatedPurchaseTaskManager.startById(id));
    }

    @PostMapping("/stop/{id}")
    @Operation(summary = "停止指定购买任务")
    public WebResponse<MonitorTaskExecuteRes> stop(@PathVariable Long id) {
        log.info("【停止指定购买任务】任务ID:{}", id);
        return WebResponse.success(designatedPurchaseTaskManager.stopById(id));
    }
}
