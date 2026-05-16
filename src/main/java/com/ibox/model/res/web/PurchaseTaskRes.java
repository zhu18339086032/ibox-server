package com.ibox.model.res.web;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量购买任务响应
 */
@Data
public class PurchaseTaskRes {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 商品ID
     */
    private Long digitalCollectionGroupId;

    /**
     * 商品名称
     */
    private String collectionName;

    /**
     * 账号ID列表
     */
    private List<Long> accountIds;

    /**
     * 账号显示名称列表
     */
    private List<String> accountNames;

    /**
     * 目标购买总数
     */
    private Integer maxCount;

    /**
     * 已购买数
     */
    private Integer purchasedCount;

    /**
     * 最大单价
     */
    private BigDecimal maxSinglePrice;

    /**
     * 间隔频率（毫秒）
     */
    private Long intervalMillis;

    /**
     * 支付平台编码
     */
    private Integer paymentPlatformCode;

    /**
     * 任务类型
     */
    private Integer taskType;

    /**
     * 任务状态
     */
    private Integer status;

    /**
     * 是否运行中
     */
    private Integer running;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
