package com.ibox.model.req.web;

import lombok.Data;

/**
 * 寄售任务分页请求
 */
@Data
public class ConsignmentTaskPageReq {

    /**
     * 页码
     */
    private long pageNum = 1;

    /**
     * 每页数量
     */
    private long pageSize = 10;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 任务状态
     */
    private Integer status;

    /**
     * 运行状态
     */
    private Integer running;
}
