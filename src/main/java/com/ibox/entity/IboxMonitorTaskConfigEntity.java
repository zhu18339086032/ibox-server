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
 * 监控任务配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_monitor_task_config")
public class IboxMonitorTaskConfigEntity extends BaseEntity {

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
     * 监控类型
     */
    @TableField("monitor_type")
    private String monitorType;

    /**
     * 账号ID
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 最大数量
     */
    @TableField("max_count")
    private Integer maxCount;

    /**
     * 最大价格
     */
    @TableField("max_price")
    private BigDecimal maxPrice;

    /**
     * 状态
     */
    @TableField("status")
    private Integer status;

    /**
     * 支付平台编码
     */
    @TableField("payment_platform_code")
    private Integer paymentPlatformCode;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;
}
