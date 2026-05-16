package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 市场藏品实体
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketCollectionRes {

    /**
     * 商品id
     */
    private Long id;

    /**
     * 藏品名称
     */
    private String name;

    /**
     * 封面图地址
     */
    private String coverPicUrl;

    /**
     * 资源文件地址
     */
    private String fileUrl;

    /**
     * 发行总量
     */
    private Long issueNum;

    /**
     * 流通数量
     */
    private Long circulationNum;

    /**
     * 地板价
     */
    private BigDecimal floorPrice;


    /**
     * 成交量
     */
    private Long tradeVolume;


    /**
     * 是否关注
     */
    private Integer focus;

    /**
     * 状态
     */
    private Integer status;
}
