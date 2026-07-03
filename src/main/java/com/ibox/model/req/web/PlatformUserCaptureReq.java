package com.ibox.model.req.web;

import lombok.Data;

/**
 * 平台用户抓取请求
 */
@Data
public class PlatformUserCaptureReq {

    /**
     * 开始用户ID
     */
    private Long startUserId;

    /**
     * 结束用户ID
     */
    private Long endUserId;

    /**
     * 账号ID
     */
    private Long accountId;
}
