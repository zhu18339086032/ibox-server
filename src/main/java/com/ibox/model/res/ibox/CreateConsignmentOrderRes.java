package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 寄售返回模型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateConsignmentOrderRes {
    private String orderId;
    private Integer specialOrderType;
}
