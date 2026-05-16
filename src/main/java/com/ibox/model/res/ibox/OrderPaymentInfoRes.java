package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 订单支付信息响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPaymentInfoRes {

    /**
     * 支付链接。
     */
    private String link;

    /**
     * 支付平台编码。
     */
    private Integer paymentPlatformCode;
}
