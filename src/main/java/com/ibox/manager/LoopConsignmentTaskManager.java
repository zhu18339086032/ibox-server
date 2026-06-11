package com.ibox.manager;

import com.ibox.model.req.web.LoopConsignmentTaskPageReq;
import com.ibox.model.req.web.LoopConsignmentTaskSaveReq;
import com.ibox.model.req.web.LoopConsignmentTaskUpdateReq;
import com.ibox.model.res.web.LoopConsignmentTaskRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;

/**
 * 循环上下架任务管理接口
 */
public interface LoopConsignmentTaskManager {
    PageResponse<LoopConsignmentTaskRes> list(LoopConsignmentTaskPageReq req);

    LoopConsignmentTaskRes getById(Long id);

    boolean save(LoopConsignmentTaskSaveReq req);

    boolean updateById(LoopConsignmentTaskUpdateReq req);

    boolean removeById(Long id);

    MonitorTaskExecuteRes startById(Long id);

    MonitorTaskExecuteRes stopById(Long id);
}
