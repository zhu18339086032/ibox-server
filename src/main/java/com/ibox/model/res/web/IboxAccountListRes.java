package com.ibox.model.res.web;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * iBox账号列表响应
 */
@Data
public class IboxAccountListRes {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 账号名称
     */
    private String accountName;

    /**
     * iBox用户ID
     */
    private Long iboxUid;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 默认支付平台编码
     */
    private Integer defaultPaymentPlatformCode;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
