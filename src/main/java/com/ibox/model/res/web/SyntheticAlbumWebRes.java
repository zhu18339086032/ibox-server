package com.ibox.model.res.web;

import lombok.Data;

/**
 * 前端材料藏品结构。
 */
@Data
public class SyntheticAlbumWebRes {

    private Long id;

    private String name;

    private String coverPicUrl;

    private String fileUrl;

    private Integer issueNum;

    private Integer usableNum;

    private Boolean needToBuy;
}
