package com.ibox.model.req.web;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增指定购买任务请求
 */
@Data
public class DesignatedPurchaseTaskSaveReq {
    private String taskName;
    private Long accountId;
    private Long targetUserId;
    private Long digitalCollectionGroupId;
    private String collectionName;
    private Integer maxCount;
    private BigDecimal maxSinglePrice;
    private Long intervalMillis;
    private Integer paymentPlatformCode;
    private Integer status;
    private String remark;
}
