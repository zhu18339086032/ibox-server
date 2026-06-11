package com.ibox.model.req.web;

import lombok.Data;

/**
 * 指定购买任务分页请求
 */
@Data
public class DesignatedPurchaseTaskPageReq {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String taskName;
    private Long accountId;
    private Long targetUserId;
    private Integer status;
    private Integer running;
}
