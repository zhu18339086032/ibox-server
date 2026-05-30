package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 合成所需的一组材料规则。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyntheticBurnAlbumGroupRes {

    private Long groupId;

    private Integer quantity;

    private Integer consumeNum;

    private List<SyntheticAlbumRes> albums;
}
