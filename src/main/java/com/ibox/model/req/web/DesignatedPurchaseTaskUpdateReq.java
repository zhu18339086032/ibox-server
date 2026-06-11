package com.ibox.model.req.web;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 修改指定购买任务请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DesignatedPurchaseTaskUpdateReq extends DesignatedPurchaseTaskSaveReq {
    private Long id;
    private Integer running;
}
