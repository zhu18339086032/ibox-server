package com.ibox.model.req.ibox;

import lombok.Data;

/**
 * 购买寄售订单请求模型
 */
@Data
public class PurchaseConsignmentOrdersReq {
    private Long digitalCollectionId;
    private Long paymentPlatformCode;
}
