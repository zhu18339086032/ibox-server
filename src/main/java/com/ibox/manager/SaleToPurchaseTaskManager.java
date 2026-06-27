package com.ibox.manager;

import com.ibox.model.req.web.SaleToPurchaseTaskPageReq;
import com.ibox.model.req.web.SaleToPurchaseTaskSaveReq;
import com.ibox.model.req.web.SaleToPurchaseTaskUpdateReq;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.SaleToPurchaseTaskRes;

/**
 * 出售求购任务管理接口
 */
public interface SaleToPurchaseTaskManager {
    PageResponse<SaleToPurchaseTaskRes> list(SaleToPurchaseTaskPageReq req);

    SaleToPurchaseTaskRes getById(Long id);

    boolean save(SaleToPurchaseTaskSaveReq req);

    boolean updateById(SaleToPurchaseTaskUpdateReq req);

    boolean removeById(Long id);

    MonitorTaskExecuteRes startById(Long id);

    MonitorTaskExecuteRes stopById(Long id);
}
