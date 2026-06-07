package com.ibox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 寄售任务实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_consignment_task")
public class ConsignmentTaskEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务名称
     */
    @TableField("task_name")
    private String taskName;

    /**
     * 账号ID
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 商品组ID
     */
    @TableField("digital_collection_group_id")
    private Long digitalCollectionGroupId;

    /**
     * 商品名称
     */
    @TableField("collection_name")
    private String collectionName;

    /**
     * 任务类型，LIST-上架，DELIST-下架
     */
    @TableField("task_type")
    private String taskType;

    /**
     * 目标数量
     */
    @TableField("target_count")
    private Integer targetCount;

    /**
     * 已执行数量
     */
    @TableField("executed_count")
    private Integer executedCount;

    /**
     * 成功数量
     */
    @TableField("success_count")
    private Integer successCount;

    /**
     * 失败数量
     */
    @TableField("fail_count")
    private Integer failCount;

    /**
     * 执行间隔，单位毫秒
     */
    @TableField("interval_millis")
    private Long intervalMillis;

    /**
     * 上架价格，下架任务可为空
     */
    @TableField("consign_price")
    private BigDecimal consignPrice;

    /**
     * 任务状态，0-禁用，1-启用
     */
    @TableField("status")
    private Integer status;

    /**
     * 是否运行中，0-否，1-是
     */
    @TableField("running")
    private Integer running;

    /**
     * 最近一次执行结果
     */
    @TableField("last_message")
    private String lastMessage;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;
}
