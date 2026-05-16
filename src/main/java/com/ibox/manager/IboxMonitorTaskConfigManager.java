package com.ibox.manager;

import com.ibox.model.req.web.IboxMonitorTaskConfigPageReq;
import com.ibox.model.req.web.IboxMonitorTaskConfigSaveReq;
import com.ibox.model.req.web.IboxMonitorTaskConfigUpdateReq;
import com.ibox.model.res.web.IboxMonitorTaskConfigRes;
import com.ibox.model.res.web.MonitorTaskExecuteRes;
import com.ibox.model.res.web.PageResponse;

/**
 * 监控任务配置服务接口
 */
public interface IboxMonitorTaskConfigManager {

    /**
     * 获取监控任务配置列表
     *
     * @param req 查询条件
     * @return 监控任务配置分页列表
     */
    PageResponse<IboxMonitorTaskConfigRes> list(IboxMonitorTaskConfigPageReq req);

    /**
     * 根据ID获取监控任务配置
     *
     * @param id 主键ID
     * @return 监控任务配置
     */
    IboxMonitorTaskConfigRes getById(Long id);

    /**
     * 保存监控任务配置
     *
     * @param req 监控任务配置保存请求
     * @return 是否成功
     */
    boolean save(IboxMonitorTaskConfigSaveReq req);

    /**
     * 更新监控任务配置
     *
     * @param req 监控任务配置修改请求
     * @return 是否成功
     */
    boolean updateById(IboxMonitorTaskConfigUpdateReq req);

    /**
     * 删除监控任务配置
     *
     * @param id 主键ID
     * @return 是否成功
     */
    boolean removeById(Long id);

    /**
     * 根据ID执行监控任务
     *
     * @param id 主键ID
     * @return 执行结果
     */
    MonitorTaskExecuteRes executeById(Long id);

    /**
     * 根据ID停止监控任务
     *
     * @param id 主键ID
     * @return 执行结果
     */
    MonitorTaskExecuteRes stopById(Long id);
}
