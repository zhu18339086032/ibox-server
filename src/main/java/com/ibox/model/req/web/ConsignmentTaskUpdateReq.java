package com.ibox.model.req.web;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 修改寄售任务请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConsignmentTaskUpdateReq extends ConsignmentTaskSaveReq {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 是否运行中
     */
    private Integer running;
}
