package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 求购单分页响应
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PurchaseOrderPageRes {
    private Boolean hasMore;
    private Integer total;
    private List<PurchaseOrderItemRes> list;
}
