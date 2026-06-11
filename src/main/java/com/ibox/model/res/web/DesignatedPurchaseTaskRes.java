package com.ibox.model.res.web;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指定购买任务响应
 */
@Data
public class DesignatedPurchaseTaskRes {
    private Long id;
    private String taskName;
    private Long accountId;
    private String accountName;
    private Long targetUserId;
    private Long digitalCollectionGroupId;
    private String collectionName;
    private Integer maxCount;
    private Integer purchasedCount;
    private BigDecimal maxSinglePrice;
    private Long intervalMillis;
    private Integer paymentPlatformCode;
    private Integer status;
    private Integer running;
    private String remark;
    private LocalDateTime createdAt;
}
