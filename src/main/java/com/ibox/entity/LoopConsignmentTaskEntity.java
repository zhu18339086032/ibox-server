package com.ibox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 循环上下架任务实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_loop_consignment_task")
public class LoopConsignmentTaskEntity extends BaseEntity {

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

    @TableField("consign_price")
    private BigDecimal consignPrice;

    @TableField("list_item_interval_millis")
    private Long listItemIntervalMillis;

    @TableField("after_list_to_delist_interval_millis")
    private Long afterListToDelistIntervalMillis;

    @TableField("delist_item_interval_millis")
    private Long delistItemIntervalMillis;

    @TableField("after_delist_to_list_interval_millis")
    private Long afterDelistToListIntervalMillis;

    @TableField("current_stage")
    private String currentStage;

    @TableField("next_execute_time")
    private LocalDateTime nextExecuteTime;

    @TableField("executed_round_count")
    private Integer executedRoundCount;

    @TableField("executed_count")
    private Integer executedCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("status")
    private Integer status;

    @TableField("running")
    private Integer running;

    @TableField("last_message")
    private String lastMessage;

    @TableField("remark")
    private String remark;
}
