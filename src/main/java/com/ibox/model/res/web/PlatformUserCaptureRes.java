package com.ibox.model.res.web;

import lombok.Data;

/**
 * 平台用户抓取结果
 */
@Data
public class PlatformUserCaptureRes {

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

    /**
     * 总扫描数量
     */
    private Integer scannedCount;

    /**
     * 存储的有效用户数量
     */
    private Integer validStoredCount;

    /**
     * 存储的不可见/锁仓/异常用户数量
     */
    private Integer invisibleStoredCount;

    /**
     * 跳过的空仓用户数量
     */
    private Integer emptySkippedCount;

    /**
     * 代理/网络重试后仍失败数量
     */
    private Integer errorCount;

    /**
     * 结果消息
     */
    private String message;
}
