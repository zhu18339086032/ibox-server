package com.ibox.model.res.web;

import lombok.Data;

import java.util.List;

/**
 * 前端通道详情结构。
 */
@Data
public class SyntheticChannelWebRes {

    private Long syntheticId;

    private String channelName;

    private String startTime;

    private String endTime;

    private Integer syntheticStatus;

    private Boolean supportAssistant;

    private Boolean supportBatchSynthetic;

    private Boolean needSlider;

    private Long surplusNum;

    private List<SyntheticBurnAlbumGroupWebRes> burnAlbums;

    private List<SyntheticTargetAlbumWebRes> targetAlbums;

    private List<SyntheticGroupNameWebRes> syntheticGroupNameList;
}
