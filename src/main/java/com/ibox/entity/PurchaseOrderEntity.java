package com.ibox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_purchase_order")
public class PurchaseOrderEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 账号ID
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 数字藏品ID
     */
    @TableField("digital_collection_id")
    private Long digitalCollectionId;

    /**
     * iBox返回的订单ID
     */
    @TableField("ibox_order_id")
    private String iboxOrderId;

    /**
     * 特殊订单类型
     */
    @TableField("special_order_type")
    private Integer specialOrderType;

    /**
     * 请求类型，1-单个下单，2-批量下单
     */
    @TableField("request_type")
    private Integer requestType;

    /**
     * 请求报文
     */
    @TableField("request_payload")
    private String requestPayload;

    /**
     * 响应报文
     */
    @TableField("response_payload")
    private String responsePayload;

    /**
     * 处理状态
     */
    @TableField("status")
    private Integer status;

    /**
     * 提交时间
     */
    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    /**
     * 完成时间
     */
    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
