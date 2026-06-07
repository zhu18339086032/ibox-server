package com.ibox.model.res.web;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 寄售任务响应
 */
@Data
public class ConsignmentTaskRes {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 账号名称
     */
    private String accountName;

    /**
     * 商品组ID
     */
    private Long digitalCollectionGroupId;

    /**
     * 商品名称
     */
    private String collectionName;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 目标数量
     */
    private Integer targetCount;

    /**
     * 已执行数量
     */
    private Integer executedCount;

    /**
     * 成功数量
     */
    private Integer successCount;

    /**
     * 失败数量
     */
    private Integer failCount;

    /**
     * 执行间隔
     */
    private Long intervalMillis;

    /**
     * 寄售价格
     */
    private BigDecimal consignPrice;

    /**
     * 任务状态
     */
    private Integer status;

    /**
     * 是否运行中
     */
    private Integer running;

    /**
     * 最近一次执行结果
     */
    private String lastMessage;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
