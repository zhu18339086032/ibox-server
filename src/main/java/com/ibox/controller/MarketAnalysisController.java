package com.ibox.controller;

import com.alibaba.fastjson.JSONObject;
import com.ibox.manager.MarketAnalysisManager;
import com.ibox.model.req.web.ConsignmentOrderAnalysisReq;
import com.ibox.model.res.web.ConsignmentOrderAnalysisRes;
import com.ibox.model.res.web.ConsignmentOrderAnalysisTaskRes;
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
 * 市场分析控制器
 */
@RestController
@RequestMapping("/ibox/market-analysis")
@Slf4j
@Tag(name = "市场分析")
public class MarketAnalysisController {

    @Autowired
    private MarketAnalysisManager marketAnalysisManager;

    @PostMapping("/consignment-orders/analyze")
    @Operation(summary = "分析商品在售挂单")
    public WebResponse<ConsignmentOrderAnalysisRes> analyzeConsignmentOrders(@RequestBody ConsignmentOrderAnalysisReq req) {
        log.info("【在售分析】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(marketAnalysisManager.analyzeConsignmentOrders(req));
    }

    @PostMapping("/consignment-orders/start")
    @Operation(summary = "异步启动商品在售分析")
    public WebResponse<ConsignmentOrderAnalysisTaskRes> startAnalyzeConsignmentOrders(@RequestBody ConsignmentOrderAnalysisReq req) {
        log.info("【在售分析-异步启动】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(marketAnalysisManager.startAnalyzeConsignmentOrders(req));
    }

    @GetMapping("/consignment-orders/task/{taskId}")
    @Operation(summary = "查询商品在售分析任务结果")
    public WebResponse<ConsignmentOrderAnalysisTaskRes> getAnalyzeTask(@PathVariable String taskId) {
        return WebResponse.success(marketAnalysisManager.getAnalyzeTask(taskId));
    }
}
