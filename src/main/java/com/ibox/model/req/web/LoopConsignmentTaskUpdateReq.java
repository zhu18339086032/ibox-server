package com.ibox.model.req.web;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 修改循环上下架任务请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoopConsignmentTaskUpdateReq extends LoopConsignmentTaskSaveReq {
    private Long id;
    private Integer running;
}
