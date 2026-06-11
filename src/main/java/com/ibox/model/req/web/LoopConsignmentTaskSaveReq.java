package com.ibox.model.req.web;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增循环上下架任务请求
 */
@Data
public class LoopConsignmentTaskSaveReq {
    private String taskName;
    private Long accountId;
    private Long digitalCollectionGroupId;
    private String collectionName;
    private BigDecimal consignPrice;
    private Long listItemIntervalMillis;
    private Long afterListToDelistIntervalMillis;
    private Long delistItemIntervalMillis;
    private Long afterDelistToListIntervalMillis;
    private Integer status;
    private String remark;
}
