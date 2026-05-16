package com.ibox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 市场藏品实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_market_collection")
public class MarketCollectionEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 藏品名称
     */
    @TableField("name")
    private String name;

    /**
     * 封面图地址
     */
    @TableField("cover_pic_url")
    private String coverPicUrl;

    /**
     * 资源文件地址
     */
    @TableField("file_url")
    private String fileUrl;

    /**
     * 发行总量
     */
    @TableField("issue_num")
    private Long issueNum;

    /**
     * 流通数量
     */
    @TableField("circulation_num")
    private Long circulationNum;

    /**
     * 地板价
     */
    @TableField("floor_price")
    private BigDecimal floorPrice;


    /**
     * 成交量
     */
    @TableField("trade_volume")
    private Long tradeVolume;


    /**
     * 是否关注
     */
    @TableField("focus")
    private Integer focus;

    /**
     * 状态
     */
    @TableField("status")
    private Integer status;
}
