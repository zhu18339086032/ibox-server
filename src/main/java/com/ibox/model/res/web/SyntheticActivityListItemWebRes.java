package com.ibox.model.res.web;

import lombok.Data;

/**
 * 前端活动列表项。
 */
@Data
public class SyntheticActivityListItemWebRes {

    private Long activityId;

    private String activityName;

    private String coverPic;

    private String startTime;

    private String endTime;

    private Boolean supportAssistant;

    private Integer syntheticStatus;

    private Integer channelCount;

    /**
     * 活动阶段：
     * UPCOMING - 即将开始
     * ONGOING  - 进行中
     */
    private String activityStage;
}
