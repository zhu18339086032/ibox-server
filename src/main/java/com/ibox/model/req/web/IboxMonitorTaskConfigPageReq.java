package com.ibox.model.req.web;

import lombok.Data;

/**
 * 监控任务配置分页查询请求
 */
@Data
public class IboxMonitorTaskConfigPageReq {

    /**
     * 当前页码
     */
    private Long pageNum = 1L;

    /**
     * 每页条数
     */
    private Long pageSize = 10L;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 监控类型
     */
    private String monitorType;

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 状态
     */
    private Integer status;
}
