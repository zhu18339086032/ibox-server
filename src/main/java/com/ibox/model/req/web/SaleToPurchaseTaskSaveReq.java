package com.ibox.model.req.web;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 出售求购任务保存请求
 */
@Data
public class SaleToPurchaseTaskSaveReq {
    private String taskName;
    private Long accountId;
    private Long digitalCollectionGroupId;
    private String collectionName;
    private BigDecimal minAcceptPrice;
    private Integer maxDealCount;
    private Long intervalMillis;
    private Integer paymentPlatformCode;
    private Integer status;
    private String remark;
}
