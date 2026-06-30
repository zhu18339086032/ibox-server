package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 在售分析专用-分页游标
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisSortValuesRes {

    private List<Object> values;
}
