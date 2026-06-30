package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 在售分析专用-寄售订单
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisConsignmentOrderRes {

    private Long id;

    private String orderUuid;

    private Long groupId;

    private Long digitalCollectionId;

    private String createdAt;

    private String doneAt;

    private String ownedAt;

    private BigDecimal price;

    private Integer orderStatus;

    private Integer orderType;

    private Integer marketType;

    private Integer secondMarketType;

    private Integer paymentPlatformCode;

    private String paymentPlatformCodes;

    private Integer isBelongUser;

    private Integer level;

    private Integer partialRefund;

    private Integer productType;

    private Integer quantity;

    private Integer specialOrderType;

    private AnalysisDigitalCollectionRes digitalCollection;

    private AnalysisDigitalCollectionGroupRes digitalCollectionGroup;
}
