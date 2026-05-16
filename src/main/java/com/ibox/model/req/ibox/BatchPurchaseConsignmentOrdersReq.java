package com.ibox.model.req.ibox;

import lombok.Data;

/**
 * 批量购买寄售订单请求模型
 */
@Data
public class BatchPurchaseConsignmentOrdersReq {
    private Long digitalCollectionGroupId;
    private Integer maxCount;
    private Integer maxSinglePrice;
    private Integer paymentPlatformCode;
    private Integer level = -1;
}
