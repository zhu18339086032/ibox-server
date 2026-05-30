package com.ibox.model.req.web;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ibox.common.MultiPatternLocalDateTimeDeserializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动合成任务新增请求。
 */
@Data
public class SyntheticTaskSaveReq {

    /**
     * 任务名称。
     * 按最新需求，前端可以留空，后端会自动生成 10 位随机编码。
     */
    private String taskName;

    /**
     * 活动 ID。
     */
    private Long activityId;

    /**
     * 活动名称。
     */
    private String activityName;

    /**
     * 通道 ID。
     */
    private Long syntheticId;

    /**
     * 通道名称。
     */
    private String channelName;

    /**
     * 账号 ID 列表。
     */
    private List<Long> accountIds;

    /**
     * 目标合成分组 ID。
     */
    private Long targetGroupId;

    /**
     * 目标合成品名称。
     */
    private String targetGroupName;

    /**
     * 材料策略。
     */
    private String materialStrategy;

    /**
     * 材料藏品 ID 列表。
     */
    private List<Long> materialAlbumIds;

    /**
     * 最大成功次数。
     * 按最新需求默认值为 5。
     */
    private Integer maxSyntheticCount;

    /**
     * 执行开始时间。
     * 前端使用 yyyy-MM-dd HH:mm:ss 格式提交。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = MultiPatternLocalDateTimeDeserializer.class)
    private LocalDateTime executeStartTime;

    /**
     * 执行结束时间。
     * 按最新需求已经不再要求填写，保留字段只为兼容后续扩展。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = MultiPatternLocalDateTimeDeserializer.class)
    private LocalDateTime executeEndTime;

    /**
     * 执行间隔毫秒。
     */
    private Long intervalMillis;

    /**
     * 状态。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;
}
