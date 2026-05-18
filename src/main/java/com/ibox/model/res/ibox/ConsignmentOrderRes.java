package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 寄售订单响应
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsignmentOrderRes {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 订单唯一标识
     */
    private String orderUuid;

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 数字藏品ID
     */
    private Long digitalCollectionId;

    /**
     * 售价-元
     */
    private BigDecimal price;

    /**
     * 订单状态 2未锁定 3已锁定
     */
    private Integer orderStatus;

    /**
     * 订单类型
     */
    private Integer orderType;

    /**
     * 市场类型
     */
    private Integer marketType;

    /**
     * 二级市场类型
     */
    private Integer secondMarketType;

    /**
     * 当前支付平台编码
     */
    private Integer paymentPlatformCode;

    /**
     * 可用支付平台编码列表
     */
    private String paymentPlatformCodes;


    /**
     * 是否属于用户 (0: 否, 1: 是)
     */
    private Integer isBelongUser;

    /**
     * 等级
     */
    private Integer level;

    /**
     * 部分退款状态 (0: 否, 1: 是)
     */
    private Integer partialRefund;

    /**
     * 产品类型
     */
    private Integer productType;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 特殊订单类型
     */
    private Integer specialOrderType;

    /**
     * 数字藏品信息
     */
    private DigitalCollectionRes digitalCollection;
}
