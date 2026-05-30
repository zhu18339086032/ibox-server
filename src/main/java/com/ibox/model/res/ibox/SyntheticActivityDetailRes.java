package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * iBox 活动详情。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyntheticActivityDetailRes {

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
     * 通道列表。
     */
    private List<SyntheticActivityChannelRes> channels;
}
