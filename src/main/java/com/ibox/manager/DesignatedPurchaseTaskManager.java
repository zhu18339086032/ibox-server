package com.ibox.manager;

import com.ibox.model.req.web.DesignatedPurchaseTaskPageReq;
import com.ibox.model.req.web.DesignatedPurchaseTaskSaveReq;
import com.ibox.model.req.web.DesignatedPurchaseTaskUpdateReq;
import com.ibox.model.res.web.DesignatedPurchaseTaskRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;

/**
 * 指定购买任务管理接口
 */
public interface DesignatedPurchaseTaskManager {
    PageResponse<DesignatedPurchaseTaskRes> list(DesignatedPurchaseTaskPageReq req);

    DesignatedPurchaseTaskRes getById(Long id);

    boolean save(DesignatedPurchaseTaskSaveReq req);

    boolean updateById(DesignatedPurchaseTaskUpdateReq req);

    boolean removeById(Long id);

    MonitorTaskExecuteRes startById(Long id);

    MonitorTaskExecuteRes stopById(Long id);
}
