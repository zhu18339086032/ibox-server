package com.ibox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * iBox账号实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_account")
public class IboxAccountEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 账号名称
     */
    @TableField("account_name")
    private String accountName;

    /**
     * iBox用户ID
     */
    @TableField("ibox_uid")
    private Long iboxUid;

    /**
     * 手机号
     */
    @TableField("phone")
    private String phone;

    /**
     * Bearer访问令牌
     */
    @TableField("bearer_token")
    private String bearerToken;

    /**
     * 默认支付平台编码
     */
    @TableField("default_payment_platform_code")
    private Integer defaultPaymentPlatformCode;
}
