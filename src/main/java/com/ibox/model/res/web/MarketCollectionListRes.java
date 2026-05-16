package com.ibox.model.res.web;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 市场藏品列表响应
 */
@Data
public class MarketCollectionListRes {

    /**
     * 商品ID
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

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
