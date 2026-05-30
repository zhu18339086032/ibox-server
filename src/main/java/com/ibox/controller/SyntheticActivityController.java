package com.ibox.controller;

import com.alibaba.fastjson.JSONObject;
import com.ibox.manager.SyntheticActivityManager;
import com.ibox.model.req.web.SyntheticActivityPageReq;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.SyntheticActivityDetailWebRes;
import com.ibox.model.res.web.SyntheticActivityListItemWebRes;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动查询控制器。
 */
@RestController
@RequestMapping("/ibox/synthetic-activity")
@Slf4j
@Tag(name = "活动合成活动")
public class SyntheticActivityController {

    @Autowired
    private SyntheticActivityManager syntheticActivityManager;

    /**
     * 查询活动列表。
     *
     * @param req 查询参数
     * @return 活动分页结果
     */
    @GetMapping("/list")
    @Operation(summary = "查询活动列表")
    public WebResponse<PageResponse<SyntheticActivityListItemWebRes>> list(SyntheticActivityPageReq req) {
        log.info("【活动合成-活动列表】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(syntheticActivityManager.list(req));
    }

    /**
     * 查询活动详情。
     *
     * @param activityId 活动 ID
     * @return 聚合后的活动详情
     */
    @GetMapping("/detail/{activityId}")
    @Operation(summary = "查询活动详情")
    public WebResponse<SyntheticActivityDetailWebRes> detail(@PathVariable Long activityId) {
        log.info("【活动合成-活动详情】activityId:{}", activityId);
        return WebResponse.success(syntheticActivityManager.detail(activityId));
    }
}
