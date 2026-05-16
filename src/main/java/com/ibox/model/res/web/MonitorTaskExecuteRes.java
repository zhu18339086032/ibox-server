package com.ibox.model.res.web;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控任务执行响应
 */
@Data
public class MonitorTaskExecuteRes {

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 监控类型
     */
    private String monitorType;

    /**
     * 是否执行成功
     */
    private Boolean success;

    /**
     * 是否正在运行
     */
    private Boolean running;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;
}
