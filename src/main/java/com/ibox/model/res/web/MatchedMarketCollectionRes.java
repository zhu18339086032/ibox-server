package com.ibox.model.res.web;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 匹配到的市场藏品响应
 */
@Data
public class MatchedMarketCollectionRes {

    /**
     * 商品ID
     */
    private Long id;

    /**
     * 藏品名称
     */
    private String name;

    /**
     * 地板价
     */
    private BigDecimal floorPrice;
}
