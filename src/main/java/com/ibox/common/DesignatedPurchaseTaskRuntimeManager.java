package com.ibox.common;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 指定购买任务运行时管理器
 */
@Component
public class DesignatedPurchaseTaskRuntimeManager {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private final Map<Long, Future<?>> runningTaskMap = new ConcurrentHashMap<>();

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
