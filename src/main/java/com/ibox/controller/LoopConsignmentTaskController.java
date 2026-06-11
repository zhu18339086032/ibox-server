package com.ibox.controller;

import com.alibaba.fastjson.JSONObject;
import com.ibox.manager.LoopConsignmentTaskManager;
import com.ibox.model.req.web.LoopConsignmentTaskPageReq;
import com.ibox.model.req.web.LoopConsignmentTaskSaveReq;
import com.ibox.model.req.web.LoopConsignmentTaskUpdateReq;
import com.ibox.model.res.web.LoopConsignmentTaskRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 循环上下架任务控制器
 */
@RestController
@RequestMapping("/ibox/loop-consignment-task")
@Slf4j
@Tag(name = "循环上下架任务")
public class LoopConsignmentTaskController {

    @Autowired
    private LoopConsignmentTaskManager loopConsignmentTaskManager;

    @GetMapping("/list")
    @Operation(summary = "获取循环上下架任务列表")
    public WebResponse<PageResponse<LoopConsignmentTaskRes>> list(LoopConsignmentTaskPageReq req) {
        log.info("【循环上下架任务列表】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(loopConsignmentTaskManager.list(req));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "根据ID获取循环上下架任务")
    public WebResponse<LoopConsignmentTaskRes> getById(@PathVariable Long id) {
        log.info("【循环上下架任务详情】任务ID:{}", id);
        return WebResponse.success(loopConsignmentTaskManager.getById(id));
    }

    @PostMapping("/save")
    @Operation(summary = "保存循环上下架任务")
    public WebResponse<Boolean> save(@RequestBody LoopConsignmentTaskSaveReq req) {
        log.info("【保存循环上下架任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(loopConsignmentTaskManager.save(req));
    }

    @PostMapping("/update")
    @Operation(summary = "修改循环上下架任务")
    public WebResponse<Boolean> update(@RequestBody LoopConsignmentTaskUpdateReq req) {
        log.info("【修改循环上下架任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(loopConsignmentTaskManager.updateById(req));
    }

    @PostMapping("/remove/{id}")
    @Operation(summary = "删除循环上下架任务")
    public WebResponse<Boolean> remove(@PathVariable Long id) {
        log.info("【删除循环上下架任务】任务ID:{}", id);
        return WebResponse.success(loopConsignmentTaskManager.removeById(id));
    }

    @PostMapping("/start/{id}")
    @Operation(summary = "启动循环上下架任务")
    public WebResponse<MonitorTaskExecuteRes> start(@PathVariable Long id) {
        log.info("【启动循环上下架任务】任务ID:{}", id);
        return WebResponse.success(loopConsignmentTaskManager.startById(id));
    }

    @PostMapping("/stop/{id}")
    @Operation(summary = "停止循环上下架任务")
    public WebResponse<MonitorTaskExecuteRes> stop(@PathVariable Long id) {
        log.info("【停止循环上下架任务】任务ID:{}", id);
        return WebResponse.success(loopConsignmentTaskManager.stopById(id));
    }
}
