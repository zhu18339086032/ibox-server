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
 * 出售求购任务实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_sale_to_purchase_task")
public class SaleToPurchaseTaskEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_name")
    private String taskName;

    @TableField("account_id")
    private Long accountId;

    @TableField("digital_collection_group_id")
    private Long digitalCollectionGroupId;

    @TableField("collection_name")
    private String collectionName;

    @TableField("min_accept_price")
    private BigDecimal minAcceptPrice;

    @TableField("max_deal_count")
    private Integer maxDealCount;

    @TableField("dealt_count")
    private Integer dealtCount;

    @TableField("interval_millis")
    private Long intervalMillis;

    @TableField("payment_platform_code")
    private Integer paymentPlatformCode;

    @TableField("status")
    private Integer status;

    @TableField("running")
    private Integer running;

    @TableField("executed_count")
    private Integer executedCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("last_message")
    private String lastMessage;

    @TableField("remark")
    private String remark;
}
