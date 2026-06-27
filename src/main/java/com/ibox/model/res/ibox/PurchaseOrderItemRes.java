package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 求购单明细
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PurchaseOrderItemRes {
    private String createdAt;
    private Long digitalCollectionId;
    private String doneAt;
    private Long groupId;
    private Long id;
    private Integer marketType;
    private Long orderRelationId;
    private Integer orderStatus;
    private Integer orderType;
    private String orderUuid;
    private Integer paymentPlatformCode;
    private BigDecimal price;
    private Integer productType;
}
