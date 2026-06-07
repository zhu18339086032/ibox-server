package com.ibox.manager;

import com.ibox.model.req.web.ConsignmentTaskPageReq;
import com.ibox.model.req.web.ConsignmentTaskSaveReq;
import com.ibox.model.req.web.ConsignmentTaskUpdateReq;
import com.ibox.model.res.web.ConsignmentTaskRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;

/**
 * 寄售任务服务接口
 */
public interface ConsignmentTaskManager {

    /**
     * 获取寄售任务列表
     */
    PageResponse<ConsignmentTaskRes> list(ConsignmentTaskPageReq req);

    /**
     * 根据ID获取寄售任务
     */
    ConsignmentTaskRes getById(Long id);

    /**
     * 保存寄售任务
     */
    boolean save(ConsignmentTaskSaveReq req);

    /**
     * 修改寄售任务
     */
    boolean updateById(ConsignmentTaskUpdateReq req);

    /**
     * 删除寄售任务
     */
    boolean removeById(Long id);

    /**
     * 启动寄售任务
     */
    MonitorTaskExecuteRes startById(Long id);

    /**
     * 停止寄售任务
     */
    MonitorTaskExecuteRes stopById(Long id);
}
