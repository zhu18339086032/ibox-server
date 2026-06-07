package com.ibox.controller;

import com.alibaba.fastjson.JSONObject;
import com.ibox.manager.ConsignmentTaskManager;
import com.ibox.model.req.web.ConsignmentTaskPageReq;
import com.ibox.model.req.web.ConsignmentTaskSaveReq;
import com.ibox.model.req.web.ConsignmentTaskUpdateReq;
import com.ibox.model.res.web.ConsignmentTaskRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 寄售任务控制器
 */
@RestController
@RequestMapping("/ibox/consignment-task")
@Slf4j
@Tag(name = "寄售任务")
public class ConsignmentTaskController {

    @Autowired
    private ConsignmentTaskManager consignmentTaskManager;

    @GetMapping("/list")
    @Operation(summary = "获取寄售任务列表")
    public WebResponse<PageResponse<ConsignmentTaskRes>> list(ConsignmentTaskPageReq req) {
        log.info("【寄售任务列表】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(consignmentTaskManager.list(req));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "根据ID获取寄售任务")
    public WebResponse<ConsignmentTaskRes> getById(@PathVariable Long id) {
        log.info("【寄售任务详情】任务ID:{}", id);
        return WebResponse.success(consignmentTaskManager.getById(id));
    }

    @PostMapping("/save")
    @Operation(summary = "保存寄售任务")
    public WebResponse<Boolean> save(@RequestBody ConsignmentTaskSaveReq req) {
        log.info("【保存寄售任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(consignmentTaskManager.save(req));
    }

    @PostMapping("/update")
    @Operation(summary = "修改寄售任务")
    public WebResponse<Boolean> update(@RequestBody ConsignmentTaskUpdateReq req) {
        log.info("【修改寄售任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(consignmentTaskManager.updateById(req));
    }

    @PostMapping("/remove/{id}")
    @Operation(summary = "删除寄售任务")
    public WebResponse<Boolean> remove(@PathVariable Long id) {
        log.info("【删除寄售任务】任务ID:{}", id);
        return WebResponse.success(consignmentTaskManager.removeById(id));
    }

    @PostMapping("/start/{id}")
    @Operation(summary = "启动寄售任务")
    public WebResponse<MonitorTaskExecuteRes> start(@PathVariable Long id) {
        log.info("【启动寄售任务】任务ID:{}", id);
        return WebResponse.success(consignmentTaskManager.startById(id));
    }

    @PostMapping("/stop/{id}")
    @Operation(summary = "停止寄售任务")
    public WebResponse<MonitorTaskExecuteRes> stop(@PathVariable Long id) {
        log.info("【停止寄售任务】任务ID:{}", id);
        return WebResponse.success(consignmentTaskManager.stopById(id));
    }
}
