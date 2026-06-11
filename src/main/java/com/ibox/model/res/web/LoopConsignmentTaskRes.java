package com.ibox.model.res.web;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 循环上下架任务响应
 */
@Data
public class LoopConsignmentTaskRes {
    private Long id;
    private String taskName;
    private Long accountId;
    private String accountName;
    private Long digitalCollectionGroupId;
    private String collectionName;
    private BigDecimal consignPrice;
    private Long listItemIntervalMillis;
    private Long afterListToDelistIntervalMillis;
    private Long delistItemIntervalMillis;
    private Long afterDelistToListIntervalMillis;
    private String currentStage;
    private LocalDateTime nextExecuteTime;
    private Integer executedRoundCount;
    private Integer executedCount;
    private Integer successCount;
    private Integer failCount;
    private Integer status;
    private Integer running;
    private String lastMessage;
    private String remark;
    private LocalDateTime createdAt;
}
