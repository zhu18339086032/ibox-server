package com.ibox.manager;

import com.ibox.model.req.web.PurchaseTaskPageReq;
import com.ibox.model.req.web.PurchaseTaskSaveReq;
import com.ibox.model.req.web.PurchaseTaskUpdateReq;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.PurchaseTaskRes;

/**
 * 批量购买任务服务接口
 */
public interface PurchaseTaskManager {

    /**
     * 获取批量购买任务列表
     *
     * @param req 查询条件
     * @return 分页列表
     */
    PageResponse<PurchaseTaskRes> list(PurchaseTaskPageReq req);

    /**
     * 根据ID获取批量购买任务
     *
     * @param id 主键ID
     * @return 任务详情
     */
    PurchaseTaskRes getById(Long id);

    /**
     * 保存批量购买任务
     *
     * @param req 保存请求
     * @return 是否成功
     */
    boolean save(PurchaseTaskSaveReq req);

    /**
     * 修改批量购买任务
     *
     * @param req 修改请求
     * @return 是否成功
     */
    boolean updateById(PurchaseTaskUpdateReq req);

    /**
     * 删除批量购买任务
     *
     * @param id 主键ID
     * @return 是否成功
     */
    boolean removeById(Long id);

    /**
     * 启动批量购买任务
     *
     * @param id 主键ID
     * @return 执行结果
     */
    MonitorTaskExecuteRes startById(Long id);

    /**
     * 停止批量购买任务
     *
     * @param id 主键ID
     * @return 执行结果
     */
    MonitorTaskExecuteRes stopById(Long id);
}
