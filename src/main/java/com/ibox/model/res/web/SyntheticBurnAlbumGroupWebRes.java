package com.ibox.model.res.web;

import lombok.Data;

import java.util.List;

/**
 * 前端材料分组结构。
 */
@Data
public class SyntheticBurnAlbumGroupWebRes {

    private Long groupId;

    private Integer quantity;

    private Integer consumeNum;

    private List<SyntheticAlbumWebRes> albums;
}
