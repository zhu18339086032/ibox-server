package com.ibox.model.req.web;

import lombok.Data;

/**
 * 批量购买任务分页查询请求
 */
@Data
public class PurchaseTaskPageReq {

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
     * 账号ID
     */
    private Long accountId;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 是否运行中
     */
    private Integer running;
}
