package com.ibox.model.req.web;

import lombok.Data;

/**
 * 循环上下架任务分页请求
 */
@Data
public class LoopConsignmentTaskPageReq {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String taskName;
    private Long accountId;
    private String currentStage;
    private Integer status;
    private Integer running;
}
