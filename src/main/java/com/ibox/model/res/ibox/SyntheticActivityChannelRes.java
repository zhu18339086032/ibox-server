package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * iBox 活动下的通道信息。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyntheticActivityChannelRes {

    /**
     * 通道对应的 syntheticId。
     */
    private Long syntheticActivityId;

    /**
     * 通道名称。
     */
    private String name;

    /**
     * 通道开始时间。
     */
    private String startTime;

    /**
     * 通道结束时间。
     */
    private String endTime;

    /**
     * 是否支持助手。
     */
    private Boolean supportAssistant;

    /**
     * 通道状态。
     */
    private Integer syntheticStatus;
}
