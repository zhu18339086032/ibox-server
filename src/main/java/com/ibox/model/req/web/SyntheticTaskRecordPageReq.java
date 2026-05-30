package com.ibox.model.req.web;

import lombok.Data;

/**
 * 活动合成执行记录分页请求。
 */
@Data
public class SyntheticTaskRecordPageReq {

    /**
     * 页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * 任务 ID。
     */
    private Long taskId;

    /**
     * 账号 ID。
     */
    private Long accountId;

    /**
     * 是否成功：0-失败，1-成功。
     */
    private Integer successFlag;
}
