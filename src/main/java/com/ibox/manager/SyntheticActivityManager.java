package com.ibox.manager;

import com.ibox.model.req.web.SyntheticActivityPageReq;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.SyntheticActivityDetailWebRes;
import com.ibox.model.res.web.SyntheticActivityListItemWebRes;

/**
 * 活动查询管理器。
 */
public interface SyntheticActivityManager {

    /**
     * 查询活动列表。
     *
     * @param req 查询条件
     * @return 前端可直接使用的分页列表
     */
    PageResponse<SyntheticActivityListItemWebRes> list(SyntheticActivityPageReq req);

    /**
     * 查询活动详情。
     *
     * @param activityId 活动 ID
     * @return 聚合后的活动详情
     */
    SyntheticActivityDetailWebRes detail(Long activityId, Long accountId);
}
