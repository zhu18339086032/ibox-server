package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 合成分组名称信息。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyntheticGroupNameRes {

    private Long syntheticGroupId;

    private String syntheticCustomDcName;

    private String syntheticCustomUrl;
}
