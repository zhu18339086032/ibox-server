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
 * 批量购买任务实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_purchase_task")
public class PurchaseTaskEntity extends BaseEntity {

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
     * 账号ID
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 目标购买总数
     */
    @TableField("max_count")
    private Integer maxCount;

    /**
     * 已购买数。
     * 每次批量购买成功后递增，用于判断任务是否达到目标总数。
     */
    @TableField("purchased_count")
    private Integer purchasedCount;

    /**
     * 最大单价
     */
    @TableField("max_single_price")
    private BigDecimal maxSinglePrice;

    /**
     * 间隔频率（毫秒）
     */
    @TableField("interval_millis")
    private Long intervalMillis;

    /**
     * 支付平台编码
     */
    @TableField("payment_platform_code")
    private Integer paymentPlatformCode;

    /**
     * 任务类型
     */
    @TableField("task_type")
    private Integer taskType;

    /**
     * 任务状态
     */
    @TableField("status")
    private Integer status;

    /**
     * 是否运行中，0-否，1-是
     */
    @TableField("running")
    private Integer running;

    /**
     * 是否开启飞书通知，0-否，1-是
     */
    @TableField("notify_feishu")
    private Integer notifyFeishu;

    /**
     * 飞书机器人webhook地址
     */
    @TableField("feishu_webhook")
    private String feishuWebhook;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;
}
