package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 数字藏品分组信息
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DigitalCollectionGroupRes {

    private Integer circulationNum;

    private String collectionInstruction;

    private String contractAddress;

    private String coverPicUrl;

    private String description;

    private String displayRarity;

    private Integer enableUpgrade;

    private Long groupId;

    private Integer groupType;

    private String headerPicUrl;

    private Long id;

    private Integer issueNum;

    private String name;

    private Integer purposeOfUse;

    private String rarity;

    private Integer structuredType;

    private Integer upgradable;
}
