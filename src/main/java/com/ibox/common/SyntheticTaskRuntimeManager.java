package com.ibox.common;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 活动合成任务运行时管理器。
 *
 * <p>这里延续现有任务系统的运行方式：
 * 每个任务在启动后都绑定一个后台线程，线程内部持续执行“单次循环逻辑”，
 * 直到被手动停止，或者执行器判断任务已经满足自动停止条件。</p>
 *
 * <p>单独拆一个运行时管理器的好处是把活动合成和购买任务隔离开，
 * 后续即使两边的调度策略不一样，也不会互相牵连。</p>
 */
@Component
public class SyntheticTaskRuntimeManager {

    /**
     * 使用缓存线程池承载后台循环任务。
     * 活动合成通常是短时高频执行，缓存线程池足够灵活。
     */
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 当前正在运行的任务句柄。
     */
    private final Map<Long, Future<?>> runningTaskMap = new ConcurrentHashMap<>();

    /**
     * 启动任务。
     *
     * @param taskId   任务 ID
     * @param runnable 实际执行逻辑
     * @return 是否成功启动
     */
    public synchronized boolean start(Long taskId, Runnable runnable) {
        if (taskId == null || runnable == null) {
            return false;
        }
        Future<?> future = runningTaskMap.get(taskId);
        if (future != null && !future.isCancelled() && !future.isDone()) {
            return false;
        }
        Future<?> newFuture = executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                runnable.run();
            }
        });
        runningTaskMap.put(taskId, newFuture);
        return true;
    }

    /**
     * 停止任务。
     *
     * @param taskId 任务 ID
     * @return 是否成功停止
     */
    public synchronized boolean stop(Long taskId) {
        if (taskId == null) {
            return false;
        }
        Future<?> future = runningTaskMap.remove(taskId);
        if (future == null) {
            return false;
        }
        future.cancel(true);
        return true;
    }

    /**
     * 判断任务是否仍在运行。
     *
     * @param taskId 任务 ID
     * @return true 表示仍在运行
     */
    public boolean isRunning(Long taskId) {
        if (taskId == null) {
            return false;
        }
        Future<?> future = runningTaskMap.get(taskId);
        return future != null && !future.isCancelled() && !future.isDone();
    }

    @PreDestroy
    public void destroy() {
        for (Future<?> future : runningTaskMap.values()) {
            future.cancel(true);
        }
        runningTaskMap.clear();
        executorService.shutdownNow();
    }
}
