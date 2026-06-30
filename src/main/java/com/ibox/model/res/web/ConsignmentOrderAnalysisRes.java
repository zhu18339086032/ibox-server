package com.ibox.model.res.web;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 在售分析响应
 */
@Data
public class ConsignmentOrderAnalysisRes {
    private Long digitalCollectionGroupId;
    private String collectionName;
    private Integer fetchedPageCount;
    private Integer rawOrderCount;
    private Integer deduplicatedOrderCount;
    private BigDecimal floorPrice;
    private BigDecimal averagePrice;
    private BigDecimal medianPrice;
    private BigDecimal top20AveragePrice;
    private BigDecimal top50AveragePrice;
    private BigDecimal top100AveragePrice;
    private BigDecimal top200AveragePrice;
    private BigDecimal top500AveragePrice;
    private List<PriceBucketRes> priceBuckets;
    private List<PressureRangeRes> pressureRanges;
    private BuySuggestionRes buySuggestion;

    @Data
    public static class PriceBucketRes {
        private BigDecimal price;
        private Integer orderCount;
        private Integer cumulativeOrderCount;
    }

    @Data
    public static class PressureRangeRes {
        private String rangeLabel;
        private BigDecimal upperPrice;
        private Integer orderCount;
    }

    @Data
    public static class BuySuggestionRes {
        private Integer score;
        private String level;
        private BigDecimal strongSafeMinPrice;
        private BigDecimal strongSafeMaxPrice;
        private BigDecimal acceptableMinPrice;
        private BigDecimal acceptableMaxPrice;
        private List<String> reasons;
    }
}
