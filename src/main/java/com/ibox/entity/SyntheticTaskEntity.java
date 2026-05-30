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
 * 活动合成任务实体。
 *
 * <p>这一张表描述“要怎样跑一条合成任务”，
 * 例如跑哪个活动、哪个通道、用哪些账号、什么时间开始、多久轮询一次。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_synthetic_task")
public class SyntheticTaskEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务名称。
     */
    @TableField("task_name")
    private String taskName;

    /**
     * 活动 ID。
     */
    @TableField("activity_id")
    private Long activityId;

    /**
     * 活动名称。
     * 冗余保存，便于前端列表直接展示。
     */
    @TableField("activity_name")
    private String activityName;

    /**
     * 合成通道 ID。
     */
    @TableField("synthetic_id")
    private Long syntheticId;

    /**
     * 通道名称。
     */
    @TableField("channel_name")
    private String channelName;

    /**
     * 账号 ID 列表，逗号分隔。
     * 第一版与购买任务保持相同存储方式，尽量减少基础设施改造。
     */
    @TableField("account_ids")
    private String accountIds;

    /**
     * 目标合成分组 ID。
     */
    @TableField("target_group_id")
    private Long targetGroupId;

    /**
     * 目标合成品名称。
     */
    @TableField("target_group_name")
    private String targetGroupName;

    /**
     * 材料策略。
     * 典型值：
     * MANUAL：手工指定材料；
     * AUTO_MATCH：运行时自动匹配。
     */
    @TableField("material_strategy")
    private String materialStrategy;

    /**
     * 手工指定材料 ID 列表，逗号分隔。
     */
    @TableField("material_album_ids")
    private String materialAlbumIds;

    /**
     * 最大成功合成次数。
     */
    @TableField("max_synthetic_count")
    private Integer maxSyntheticCount;

    /**
     * 当前累计成功次数。
     */
    @TableField("success_count")
    private Integer successCount;

    /**
     * 定时开始时间。
     */
    @TableField("execute_start_time")
    private LocalDateTime executeStartTime;

    /**
     * 定时结束时间。
     */
    @TableField("execute_end_time")
    private LocalDateTime executeEndTime;

    /**
     * 两次执行之间的间隔毫秒数。
     */
    @TableField("interval_millis")
    private Long intervalMillis;

    /**
     * 任务状态：0-禁用，1-启用。
     */
    @TableField("status")
    private Integer status;

    /**
     * 是否正在运行：0-否，1-是。
     */
    @TableField("running")
    private Integer running;

    /**
     * 最近一次执行时间。
     */
    @TableField("last_execute_time")
    private LocalDateTime lastExecuteTime;

    /**
     * 最近一次结果码。
     */
    @TableField("last_result_code")
    private String lastResultCode;

    /**
     * 最近一次结果说明。
     */
    @TableField("last_result_message")
    private String lastResultMessage;

    /**
     * 备注。
     */
    @TableField("remark")
    private String remark;
}
