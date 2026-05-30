package com.ibox.model.res.web;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动合成执行记录响应。
 */
@Data
public class SyntheticTaskRecordRes {

    private Long id;

    private Long taskId;

    private Long accountId;

    private String accountName;

    private Long activityId;

    private Long syntheticId;

    private String requestPayload;

    private String responsePayload;

    private String resultCode;

    private String resultMessage;

    private Integer successFlag;

    private String targetTokenIds;

    private LocalDateTime createdAt;
}
