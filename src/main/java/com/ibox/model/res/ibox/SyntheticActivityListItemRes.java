package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * iBox 活动列表项。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyntheticActivityListItemRes {

    /**
     * 活动 ID。
     */
    private Long id;

    /**
     * 活动名称。
     */
    private String name;

    /**
     * 封面图。
     */
    private String coverPic;

    /**
     * 开始时间。
     */
    private String startTime;

    /**
     * 结束时间。
     */
    private String endTime;

    /**
     * 是否支持助手。
     */
    private Boolean supportAssistant;

    /**
     * 活动状态。
     */
    private Integer syntheticStatus;
}
