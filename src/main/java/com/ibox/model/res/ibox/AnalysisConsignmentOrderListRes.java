package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 在售分析专用-寄售订单分页结果
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisConsignmentOrderListRes {

    private boolean hasMore;

    private List<AnalysisConsignmentOrderRes> list;

    private AnalysisSortValuesRes sortValues;

    private Integer total;
}
