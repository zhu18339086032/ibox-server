package com.ibox.model.res.web;

import lombok.Data;

import java.util.List;

/**
 * 前端活动详情响应。
 *
 * <p>这是一份聚合后的结构，前端拿到它就能直接渲染活动卡片和创建任务弹窗。</p>
 */
@Data
public class SyntheticActivityDetailWebRes {

    private Long activityId;

    private String activityName;

    private String coverPic;

    private String startTime;

    private String endTime;

    private Integer syntheticStatus;

    private Boolean supportAssistant;

    private List<SyntheticChannelWebRes> channels;
}
