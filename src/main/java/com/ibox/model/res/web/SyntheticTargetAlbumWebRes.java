package com.ibox.model.res.web;

import lombok.Data;

/**
 * 前端目标合成品结构。
 */
@Data
public class SyntheticTargetAlbumWebRes {

    private Long id;

    private String name;

    private String coverPicUrl;

    private String fileUrl;

    private Integer issueNum;

    private Integer circulationNum;

    private Integer quantity;

    private Long syntheticGroupId;
}
