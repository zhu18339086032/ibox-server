package com.ibox.common;

import com.ibox.model.res.web.ConsignmentOrderAnalysisRes;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 在售分析异步任务管理器
 */
@Component
public class MarketAnalysisTaskManager {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private final Map<String, AnalysisTaskSnapshot> taskMap = new ConcurrentHashMap<>();

    public AnalysisTaskSnapshot createTask() {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        AnalysisTaskSnapshot snapshot = new AnalysisTaskSnapshot();
        snapshot.setTaskId(taskId);
        snapshot.setStatus("PENDING");
        snapshot.setProgress(0);
        snapshot.setMessage("任务已创建");
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshot.setUpdatedAt(LocalDateTime.now());
        taskMap.put(taskId, snapshot);
        return snapshot;
    }

    public void submit(String taskId, Runnable runnable) {
        executorService.submit(runnable);
    }

    public AnalysisTaskSnapshot getTask(String taskId) {
        return taskMap.get(taskId);
    }

    public void markRunning(String taskId, int progress, String message) {
        update(taskId, "RUNNING", progress, message, null);
    }

    public void markSuccess(String taskId, ConsignmentOrderAnalysisRes result) {
        update(taskId, "SUCCESS", 100, "分析完成", result);
    }

    public void markFail(String taskId, String message) {
        update(taskId, "FAIL", 100, message, null);
    }

    private void update(String taskId, String status, int progress, String message, ConsignmentOrderAnalysisRes result) {
        AnalysisTaskSnapshot snapshot = taskMap.get(taskId);
        if (snapshot == null) {
            return;
        }
        snapshot.setStatus(status);
        snapshot.setProgress(progress);
        snapshot.setMessage(message);
        snapshot.setResult(result);
        snapshot.setUpdatedAt(LocalDateTime.now());
    }

    @PreDestroy
    public void destroy() {
        taskMap.clear();
        executorService.shutdownNow();
    }

    public static class AnalysisTaskSnapshot {
        private String taskId;
        private String status;
        private Integer progress;
        private String message;
        private ConsignmentOrderAnalysisRes result;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getProgress() {
            return progress;
        }

        public void setProgress(Integer progress) {
            this.progress = progress;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public ConsignmentOrderAnalysisRes getResult() {
            return result;
        }

        public void setResult(ConsignmentOrderAnalysisRes result) {
            this.result = result;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
