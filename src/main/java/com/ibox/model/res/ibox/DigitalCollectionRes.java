package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 寄售订单响应
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DigitalCollectionRes {

    /**
     * 数字藏品ID
     */
    private Long id;

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 数字藏品名称
     */
    private String name;

    /**
     * 文件URL
     */
    private String fileUrl;

    /**
     * Token ID
     */
    private Long tokenId;

    /**
     * 拥有者钱包地址
     */
    private String ownerWalletAddress;

    /**
     * 锁定状态
     */
    private Integer lockStatus;

    /**
     * 锁定类型，2表示可下单
     */
    private Integer lockType;
}
