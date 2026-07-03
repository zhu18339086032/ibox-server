package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 平台用户抓取专用-用户仓库商品摘要
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlatformUserCollectionGroupItemRes {

    /**
     * 寄售数量
     */
    private Integer consignmentNum;

    /**
     * 寄售状态
     */
    private Integer consignmentStatus;

    /**
     * 封面图
     */
    private String coverPicUrl;

    /**
     * 展示稀有度
     */
    private String displayRarity;

    /**
     * 是否可寄售
     */
    private Integer enableConsignment;

    /**
     * 是否可升级
     */
    private Integer enableUpgrade;

    /**
     * 分组类型
     */
    private Integer groupType;

    /**
     * 头图
     */
    private String headerPicUrl;

    /**
     * 持有数量
     */
    private Integer holdNum;

    /**
     * 商品ID
     */
    private Long id;

    /**
     * 是否可合成
     */
    private Integer isEnableSynthetic;

    /**
     * 是否元宇宙
     */
    private Integer isMetaVerse;

    /**
     * 发行数量
     */
    private Integer issueNum;

    /**
     * 锁定数量
     */
    private Integer lockCount;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 最近持有时间
     */
    private String ownedAt;

    /**
     * 平台ID
     */
    private Integer platformId;

    /**
     * 稀有度
     */
    private String rarity;

    /**
     * 结构化类型
     */
    private Integer structuredType;

    /**
     * 合成数量
     */
    private Integer syntheticNum;

    /**
     * 合成进行中数量
     */
    private Integer syntheticProgressNum;
}
