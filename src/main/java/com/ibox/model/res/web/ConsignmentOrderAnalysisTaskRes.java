package com.ibox.model.res.web;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在售分析任务响应
 */
@Data
public class ConsignmentOrderAnalysisTaskRes {
    private String taskId;
    private String status;
    private Integer progress;
    private String message;
    private ConsignmentOrderAnalysisRes result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
