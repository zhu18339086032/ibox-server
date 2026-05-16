package com.ibox.model.res.ibox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 寄售订单响应
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsignmentOrderRes {


    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 寄售订单ID
     */
    private Long consignmentOrderId;

    /**
     * 订单唯一标识
     */
    private String orderUuid;

    /**
     * 分组ID
     */
    private String groupId;

    /**
     * 数字藏品ID
     */
    private Long digitalCollectionId;

    /**
     * 数字藏品名称
     */
    private String digitalCollectionName;

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
     * 锁定状态
     */
    private Integer lockStatus;

    /**
     * 锁定类型，2表示可下单
     */
    private Integer lockType;

    /**
     * 当前支付平台编码
     */
    private Integer paymentPlatformCode;

    /**
     * 可用支付平台编码列表
     */
    private String paymentPlatformCodes;
}
