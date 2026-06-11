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
 * 指定购买任务实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_designated_purchase_task")
public class DesignatedPurchaseTaskEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_name")
    private String taskName;

    @TableField("account_id")
    private Long accountId;

    @TableField("target_user_id")
    private Long targetUserId;

    @TableField("digital_collection_group_id")
    private Long digitalCollectionGroupId;

    @TableField("collection_name")
    private String collectionName;

    @TableField("max_count")
    private Integer maxCount;

    @TableField("purchased_count")
    private Integer purchasedCount;

    @TableField("max_single_price")
    private BigDecimal maxSinglePrice;

    @TableField("interval_millis")
    private Long intervalMillis;

    @TableField("payment_platform_code")
    private Integer paymentPlatformCode;

    @TableField("status")
    private Integer status;

    @TableField("running")
    private Integer running;

    @TableField("remark")
    private String remark;
}
