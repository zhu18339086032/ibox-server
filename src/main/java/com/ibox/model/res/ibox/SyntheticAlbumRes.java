package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 合成材料藏品信息。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyntheticAlbumRes {

    private Long id;

    private String name;

    private String coverPicUrl;

    private String fileUrl;

    private Integer issueNum;

    private Integer usableNum;

    private Boolean needToBuy;
}
