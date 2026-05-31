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
import org.springframework.web.bind.annotation.RequestParam;
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
     * @return 分页活动列表
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
     * <p>注意：活动详情、center detail 都要走当前账号 token，
     * 这样前端看到的资格、材料和通道状态才与最终执行账号一致。</p>
     *
     * @param activityId 活动 ID
     * @param accountId 账号 ID
     * @return 活动详情
     */
    @GetMapping("/detail/{activityId}")
    @Operation(summary = "查询活动详情")
    public WebResponse<SyntheticActivityDetailWebRes> detail(@PathVariable Long activityId, @RequestParam Long accountId) {
        log.info("【活动合成-活动详情】activityId:{}, accountId:{}", activityId, accountId);
        return WebResponse.success(syntheticActivityManager.detail(activityId, accountId));
    }
}
