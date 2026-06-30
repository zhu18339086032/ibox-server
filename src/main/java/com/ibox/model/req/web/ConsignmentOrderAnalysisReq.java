package com.ibox.model.req.web;

import lombok.Data;

/**
 * 在售分析请求
 */
@Data
public class ConsignmentOrderAnalysisReq {
    private Long digitalCollectionGroupId;
    private Integer pageSize;
    private Integer maxPages;
    private Long pageIntervalMillis;
}
