package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 寄售订单响应
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsignmentOrderListRes {
    private boolean hasMore;
    private List<ConsignmentOrderRes> list;
}
