package com.ibox.controller;

import com.alibaba.fastjson.JSONObject;
import com.ibox.manager.SyntheticTaskManager;
import com.ibox.model.req.web.SyntheticTaskPageReq;
import com.ibox.model.req.web.SyntheticTaskRecordPageReq;
import com.ibox.model.req.web.SyntheticTaskSaveReq;
import com.ibox.model.req.web.SyntheticTaskUpdateReq;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.SyntheticTaskRecordRes;
import com.ibox.model.res.web.SyntheticTaskRes;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 活动合成任务控制器。
 */
@RestController
@RequestMapping("/ibox/synthetic-task")
@Slf4j
@Tag(name = "活动合成任务")
public class SyntheticTaskController {

    @Autowired
    private SyntheticTaskManager syntheticTaskManager;

    /**
     * 查询任务列表。
     *
     * @param req 查询参数
     * @return 分页任务列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询任务列表")
    public WebResponse<PageResponse<SyntheticTaskRes>> list(SyntheticTaskPageReq req) {
        log.info("【活动合成-任务列表】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(syntheticTaskManager.list(req));
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @GetMapping("/get/{id}")
    @Operation(summary = "查询任务详情")
    public WebResponse<SyntheticTaskRes> getById(@PathVariable Long id) {
        log.info("【活动合成-任务详情】id:{}", id);
        return WebResponse.success(syntheticTaskManager.getById(id));
    }

    /**
     * 查询执行记录。
     *
     * @param req 查询参数
     * @return 执行记录分页结果
     */
    @GetMapping("/record/list")
    @Operation(summary = "查询执行记录")
    public WebResponse<PageResponse<SyntheticTaskRecordRes>> recordList(SyntheticTaskRecordPageReq req) {
        log.info("【活动合成-执行记录】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(syntheticTaskManager.recordList(req));
    }

    /**
     * 保存任务。
     *
     * @param req 保存请求
     * @return 是否成功
     */
    @PostMapping("/save")
    @Operation(summary = "保存任务")
    public WebResponse<Boolean> save(@RequestBody SyntheticTaskSaveReq req) {
        log.info("【活动合成-保存任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(syntheticTaskManager.save(req));
    }

    /**
     * 修改任务。
     *
     * @param req 修改请求
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改任务")
    public WebResponse<Boolean> update(@RequestBody SyntheticTaskUpdateReq req) {
        log.info("【活动合成-修改任务】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(syntheticTaskManager.updateById(req));
    }

    /**
     * 删除任务。
     *
     * @param id 任务 ID
     * @return 是否成功
     */
    @PostMapping("/remove/{id}")
    @Operation(summary = "删除任务")
    public WebResponse<Boolean> remove(@PathVariable Long id) {
        log.info("【活动合成-删除任务】id:{}", id);
        return WebResponse.success(syntheticTaskManager.removeById(id));
    }

    /**
     * 启动任务。
     *
     * @param id 任务 ID
     * @return 启动结果
     */
    @PostMapping("/start/{id}")
    @Operation(summary = "启动任务")
    public WebResponse<MonitorTaskExecuteRes> start(@PathVariable Long id) {
        log.info("【活动合成-启动任务】id:{}", id);
        return WebResponse.success(syntheticTaskManager.startById(id));
    }

    /**
     * 停止任务。
     *
     * @param id 任务 ID
     * @return 停止结果
     */
    @PostMapping("/stop/{id}")
    @Operation(summary = "停止任务")
    public WebResponse<MonitorTaskExecuteRes> stop(@PathVariable Long id) {
        log.info("【活动合成-停止任务】id:{}", id);
        return WebResponse.success(syntheticTaskManager.stopById(id));
    }
}
