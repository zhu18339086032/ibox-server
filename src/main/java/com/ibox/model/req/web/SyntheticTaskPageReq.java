package com.ibox.model.req.web;

import lombok.Data;

/**
 * 活动合成任务分页请求。
 */
@Data
public class SyntheticTaskPageReq {

    /**
     * 页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * 任务名称。
     */
    private String taskName;

    /**
     * 任务状态：0-禁用，1-启用。
     */
    private Integer status;

    /**
     * 是否运行中：0-否，1-是。
     */
    private Integer running;

    /**
     * 活动 ID。
     */
    private Long activityId;

    /**
     * 账号 ID。
     */
    private Long accountId;
}
