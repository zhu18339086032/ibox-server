package com.ibox.manager;

import com.ibox.model.req.web.MarketCollectionPageReq;
import com.ibox.model.res.web.MarketCollectionListRes;
import com.ibox.model.res.web.PageResponse;

import java.util.List;

/**
 * 市场藏品服务接口
 */
public interface MarketCollectionManager {

    /**
     * 全量获取市场列表
     *
     * @return 市场列表
     */
    List<MarketCollectionListRes> list();

    /**
     * 分页获取市场列表
     *
     * @param req 分页查询参数
     * @return 分页结果
     */
    PageResponse<MarketCollectionListRes> page(MarketCollectionPageReq req);

    /**
     * 刷新市场列表
     *
     * @return 刷新数量
     */
    Integer refresh();
}
