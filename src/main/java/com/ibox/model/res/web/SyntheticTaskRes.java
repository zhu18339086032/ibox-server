package com.ibox.model.res.web;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动合成任务响应。
 */
@Data
public class SyntheticTaskRes {

    private Long id;

    private String taskName;

    private Long activityId;

    private String activityName;

    private Long syntheticId;

    private String channelName;

    private List<Long> accountIds;

    private List<String> accountNames;

    private Long targetGroupId;

    private String targetGroupName;

    private String materialStrategy;

    private List<Long> materialAlbumIds;

    private Integer maxSyntheticCount;

    private Integer successCount;

    private LocalDateTime executeStartTime;

    private LocalDateTime executeEndTime;

    private Long intervalMillis;

    private Integer status;

    private Integer running;

    private LocalDateTime lastExecuteTime;

    private String lastResultCode;

    private String lastResultMessage;

    private String remark;

    private LocalDateTime createdAt;
}
