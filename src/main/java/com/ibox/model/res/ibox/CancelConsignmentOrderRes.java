package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 撤销寄售返回模型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public  class CancelConsignmentOrderRes {
    private String orderUuid;
    private Integer specialOrderType;
}