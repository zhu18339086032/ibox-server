package com.ibox.model.req.web;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 出售求购任务更新请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SaleToPurchaseTaskUpdateReq extends SaleToPurchaseTaskSaveReq {
    private Long id;
    private Integer running;
}
