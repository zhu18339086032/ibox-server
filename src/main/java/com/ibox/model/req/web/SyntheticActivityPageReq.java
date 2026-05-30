package com.ibox.model.req.web;

import lombok.Data;

/**
 * 活动列表查询请求。
 */
@Data
public class SyntheticActivityPageReq {

    /**
     * 页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * 活动名关键字。
     */
    private String keyword;

    /**
     * 是否只看可用活动。
     */
    private Boolean onlyAvailable;

    /**
     * 是否筛选支持助手的活动。
     */
    private Boolean supportAssistant;
}
