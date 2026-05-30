package com.ibox.manager;

import com.ibox.model.req.web.SyntheticTaskPageReq;
import com.ibox.model.req.web.SyntheticTaskRecordPageReq;
import com.ibox.model.req.web.SyntheticTaskSaveReq;
import com.ibox.model.req.web.SyntheticTaskUpdateReq;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.SyntheticTaskRecordRes;
import com.ibox.model.res.web.SyntheticTaskRes;

/**
 * 活动合成任务管理器。
 */
public interface SyntheticTaskManager {

    /**
     * 查询任务列表。
     */
    PageResponse<SyntheticTaskRes> list(SyntheticTaskPageReq req);

    /**
     * 查询任务详情。
     */
    SyntheticTaskRes getById(Long id);

    /**
     * 查询执行记录。
     */
    PageResponse<SyntheticTaskRecordRes> recordList(SyntheticTaskRecordPageReq req);

    /**
     * 保存任务。
     */
    boolean save(SyntheticTaskSaveReq req);

    /**
     * 更新任务。
     */
    boolean updateById(SyntheticTaskUpdateReq req);

    /**
     * 删除任务。
     */
    boolean removeById(Long id);

    /**
     * 启动任务。
     */
    MonitorTaskExecuteRes startById(Long id);

    /**
     * 停止任务。
     */
    MonitorTaskExecuteRes stopById(Long id);
}
