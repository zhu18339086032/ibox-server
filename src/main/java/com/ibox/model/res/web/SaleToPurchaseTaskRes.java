package com.ibox.model.res.web;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出售求购任务响应
 */
@Data
public class SaleToPurchaseTaskRes {
    private Long id;
    private String taskName;
    private Long accountId;
    private String accountName;
    private Long digitalCollectionGroupId;
    private String collectionName;
    private BigDecimal minAcceptPrice;
    private Integer maxDealCount;
    private Integer dealtCount;
    private Long intervalMillis;
    private Integer paymentPlatformCode;
    private Integer status;
    private Integer running;
    private Integer executedCount;
    private Integer successCount;
    private Integer failCount;
    private String lastMessage;
    private String remark;
    private LocalDateTime createdAt;
}
