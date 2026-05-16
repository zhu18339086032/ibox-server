package com.ibox.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公告与推荐位监控状态缓存。
 */
@Component
public class AnnouncementStateCache {

    /**
     * 最近公告 ID。
     */
    @Setter
    @Getter
    private volatile Long latestAnnouncementId;

    /**
     * 推荐位已处理 ID，按监控任务隔离。
     */
    private final Map<Long, Set<Long>> recommendAreaSeenIds = new ConcurrentHashMap<>();

    public Set<Long> getOrCreateRecommendAreaSeenIds(Long taskId) {
        return recommendAreaSeenIds.computeIfAbsent(taskId, key -> ConcurrentHashMap.newKeySet());
    }

    public void clearRecommendAreaSeenIds(Long taskId) {
        if (taskId == null) {
            return;
        }
        recommendAreaSeenIds.remove(taskId);
    }
}
