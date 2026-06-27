package com.ibox.model.req.web;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 出售求购任务分页请求
 */
@Data
public class SaleToPurchaseTaskPageReq {
    private String taskName;
    private Long accountId;
    private Integer status;
    private Integer running;
    /**
     * 当前页码
     */
    private Long pageNum = 1L;

    /**
     * 每页条数
     */
    private Long pageSize = 10L;
}
