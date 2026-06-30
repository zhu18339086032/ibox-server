package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 在售分析专用-数字藏品信息
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisDigitalCollectionRes {

    private Long id;

    private Long groupId;

    private String name;

    private String fileUrl;

    private Long tokenId;

    private String ownedAt;

    private String ownerWalletAddress;

    private Integer lockStatus;

    private Integer lockType;
}
