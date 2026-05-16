package com.ibox.model.req.web;

import lombok.Data;

/**
 * 市场藏品分页查询请求
 */
@Data
public class MarketCollectionPageReq {

    /**
     * 当前页码
     */
    private Long pageNum = 1L;

    /**
     * 每页条数
     */
    private Long pageSize = 10L;

    /**
     * 藏品名称
     */
    private String name;
}
