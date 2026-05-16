package com.ibox.controller;

import com.alibaba.fastjson.JSONObject;
import com.ibox.manager.PurchaseTaskManager;
import com.ibox.model.req.web.PurchaseTaskPageReq;
import com.ibox.model.req.web.PurchaseTaskSaveReq;
import com.ibox.model.req.web.PurchaseTaskUpdateReq;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.PurchaseTaskRes;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 批量购买任务控制器
 */
@RestController
@RequestMapping("/ibox/purchase-task")
@Slf4j
@Tag(name = "批量购买任务")
public class PurchaseTaskController {

    /**
     * 批量购买任务服务
     */
    @Autowired
    private PurchaseTaskManager purchaseTaskManager;

    @GetMapping("/list")
    @Operation(summary = "获取批量购买任务列表")
    public WebResponse<PageResponse<PurchaseTaskRes>> list(PurchaseTaskPageReq req) {
        log.info("【批量购买任务列表】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(purchaseTaskManager.list(req));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "根据ID获取批量购买任务")
    public WebResponse<PurchaseTaskRes> getById(@PathVariable Long id) {
        log.info("【批量购买任务详情】任务ID:{}", id);
        return WebResponse.success(purchaseTaskManager.getById(id));
    }

    @PostMapping("/save")
    @Operation(summary = "保存批量购买任务")
    public WebResponse<Boolean> save(@RequestBody PurchaseTaskSaveReq req) {
        log.info("【保存批量购买任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(purchaseTaskManager.save(req));
    }

    @PostMapping("/update")
    @Operation(summary = "修改批量购买任务")
    public WebResponse<Boolean> update(@RequestBody PurchaseTaskUpdateReq req) {
        log.info("【修改批量购买任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(purchaseTaskManager.updateById(req));
    }

    @PostMapping("/remove/{id}")
    @Operation(summary = "删除批量购买任务")
    public WebResponse<Boolean> remove(@PathVariable Long id) {
        log.info("【删除批量购买任务】任务ID:{}", id);
        return WebResponse.success(purchaseTaskManager.removeById(id));
    }

    @PostMapping("/start/{id}")
    @Operation(summary = "启动批量购买任务")
    public WebResponse<MonitorTaskExecuteRes> start(@PathVariable Long id) {
        log.info("【启动批量购买任务】任务ID:{}", id);
        return WebResponse.success(purchaseTaskManager.startById(id));
    }

    @PostMapping("/stop/{id}")
    @Operation(summary = "停止批量购买任务")
    public WebResponse<MonitorTaskExecuteRes> stop(@PathVariable Long id) {
        log.info("【停止批量购买任务】任务ID:{}", id);
        return WebResponse.success(purchaseTaskManager.stopById(id));
    }
}
