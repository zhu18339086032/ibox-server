package com.ibox.manager;

import com.ibox.entity.IboxMonitorTaskConfigEntity;

/**
 * 公告与推荐位监控服务接口。
 */
public interface AnnouncementMonitorManager {

    /**
     * 按监控任务配置执行公告监控。
     *
     * @param config 监控任务配置
     */
    void monitorLatestAnnouncement(IboxMonitorTaskConfigEntity config);

    /**
     * 按监控任务配置执行推荐位监控。
     *
     * @param config 监控任务配置
     */
    void monitorRecommendArea(IboxMonitorTaskConfigEntity config);

    /**
     * 清理推荐位监控运行时状态。
     *
     * @param taskId 监控任务 ID
     */
    void clearRecommendAreaState(Long taskId);
}
