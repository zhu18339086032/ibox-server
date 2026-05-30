package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * iBox 合成中心详情。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyntheticCenterRes {

    private Long id;

    private String name;

    private String description;

    private String coverPic;

    private Long startTime;

    private Long endTime;

    private Integer syntheticStatus;

    private Integer supportAssistant;

    private Integer supportBatchSynthetic;

    private Integer needSlider;

    private Long surplusNum;

    private List<SyntheticBurnAlbumGroupRes> burnAlbums;

    private List<SyntheticTargetAlbumRes> targetAlbums;

    private List<SyntheticGroupNameRes> syntheticGroupNameList;
}
