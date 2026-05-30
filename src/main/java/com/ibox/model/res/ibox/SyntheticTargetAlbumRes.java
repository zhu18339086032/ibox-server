package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 目标合成品信息。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyntheticTargetAlbumRes {

    private Long id;

    private String name;

    private String coverPicUrl;

    private String fileUrl;

    private Integer issueNum;

    private Integer circulationNum;

    private Integer quantity;

    private Long syntheticGroupId;
}
