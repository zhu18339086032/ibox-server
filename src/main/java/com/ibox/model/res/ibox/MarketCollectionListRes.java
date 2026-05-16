package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/**
 * 市场藏品列表响应
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketCollectionListRes {

    /**
     * 藏品列表
     */
    private List<MarketCollectionRes> list;

    /**
     * 总数
     */
    private Long total;

    /**
     * 是否还有更多数据
     */
    private Boolean hasMore;
}