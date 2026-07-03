package com.ibox.controller;

import com.ibox.manager.PlatformUserCaptureManager;
import com.ibox.model.req.web.PlatformUserCaptureReq;
import com.ibox.model.res.web.PlatformUserCaptureRes;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台用户抓取控制器
 */
@RestController
@RequestMapping("/ibox/platform-user")
@Slf4j
@Tag(name = "平台用户抓取")
public class PlatformUserCaptureController {

    @Autowired
    private PlatformUserCaptureManager platformUserCaptureManager;

    @PostMapping("/capture")
    @Operation(summary = "抓取并存储平台用户")
    public WebResponse<PlatformUserCaptureRes> capture(@RequestBody PlatformUserCaptureReq req) {
        log.info("【平台用户抓取】开始执行，startUserId:{}, endUserId:{}, accountId:{}",
                req == null ? null : req.getStartUserId(),
                req == null ? null : req.getEndUserId(),
                req == null ? null : req.getAccountId());
        return WebResponse.success(platformUserCaptureManager.capture(req));
    }
}
