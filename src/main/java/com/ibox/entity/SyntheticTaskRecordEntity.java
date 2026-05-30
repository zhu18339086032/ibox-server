package com.ibox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 活动合成执行记录实体。
 *
 * <p>任务主表只保留汇总状态，具体某次执行的请求和结果都落到这里，
 * 方便做排障和复盘。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_synthetic_task_record")
public class SyntheticTaskRecordEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属任务 ID。
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 执行账号 ID。
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 活动 ID。
     */
    @TableField("activity_id")
    private Long activityId;

    /**
     * 合成通道 ID。
     */
    @TableField("synthetic_id")
    private Long syntheticId;

    /**
     * 请求体快照。
     */
    @TableField("request_payload")
    private String requestPayload;

    /**
     * 响应体快照。
     */
    @TableField("response_payload")
    private String responsePayload;

    /**
     * 结果码。
     */
    @TableField("result_code")
    private String resultCode;

    /**
     * 结果说明。
     */
    @TableField("result_message")
    private String resultMessage;

    /**
     * 是否成功：0-失败，1-成功。
     */
    @TableField("success_flag")
    private Integer successFlag;

    /**
     * 成功后返回的 tokenId 列表，逗号分隔。
     */
    @TableField("target_token_ids")
    private String targetTokenIds;
}
