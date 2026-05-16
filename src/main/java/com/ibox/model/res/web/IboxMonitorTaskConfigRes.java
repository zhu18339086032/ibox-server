package com.ibox.model.res.web;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 监控任务配置响应
 */
@Data
public class IboxMonitorTaskConfigRes {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 监控类型
     */
    private String monitorType;

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 最大数量
     */
    private Integer maxCount;

    /**
     * 最大价格
     */
    private BigDecimal maxPrice;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 是否运行中，0-否，1-是
     */
    private Integer running;

    /**
     * 支付平台编码
     */
    private Integer paymentPlatformCode;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}