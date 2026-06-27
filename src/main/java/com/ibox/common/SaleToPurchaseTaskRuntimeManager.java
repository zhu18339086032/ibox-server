package com.ibox.common;

import com.ibox.model.res.ibox.UserDigitalCollectionItemRes;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 出售求购任务运行时管理器
 */
@Component
public class SaleToPurchaseTaskRuntimeManager {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private final Map<Long, Future<?>> runningTaskMap = new ConcurrentHashMap<>();

    private final Map<Long, SaleInventoryContext> inventoryContextMap = new ConcurrentHashMap<>();

    public synchronized boolean start(Long taskId, Runnable runnable, List<UserDigitalCollectionItemRes> sellableItems) {
        if (taskId == null || runnable == null) {
            return false;
        }
        Future<?> future = runningTaskMap.get(taskId);
        if (future != null && !future.isCancelled() && !future.isDone()) {
            return false;
        }
        inventoryContextMap.put(taskId, new SaleInventoryContext(sellableItems));
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
        inventoryContextMap.remove(taskId);
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

    public SaleInventoryContext getContext(Long taskId) {
        return taskId == null ? null : inventoryContextMap.get(taskId);
    }

    @PreDestroy
    public void destroy() {
        for (Future<?> future : runningTaskMap.values()) {
            future.cancel(true);
        }
        runningTaskMap.clear();
        inventoryContextMap.clear();
        executorService.shutdownNow();
    }

    public static class SaleInventoryContext {
        private final Deque<UserDigitalCollectionItemRes> remainingItems = new ArrayDeque<>();

        public SaleInventoryContext(List<UserDigitalCollectionItemRes> items) {
            if (items != null) {
                remainingItems.addAll(items);
            }
        }

        public synchronized UserDigitalCollectionItemRes peek() {
            return remainingItems.peekFirst();
        }

        public synchronized UserDigitalCollectionItemRes poll() {
            return remainingItems.pollFirst();
        }

        public synchronized boolean isEmpty() {
            return remainingItems.isEmpty();
        }

        public synchronized int size() {
            return remainingItems.size();
        }

        public synchronized List<UserDigitalCollectionItemRes> snapshot() {
            return new ArrayList<>(remainingItems);
        }
    }
}
