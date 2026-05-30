-- =========================================================
-- ibox-server 活动合成任务增量脚本
-- 说明：
-- 1. 该脚本用于把“活动合成任务”相关表补充到现有库中。
-- 2. 若是全新环境初始化，建议同时参考 ibox_server.sql 中的完整建表语句。
-- 3. 当前规则已经和前后端实现保持一致：
--    - 任务名称允许前端不传，后端会自动生成 10 位随机编码
--    - 活动结束后不再展示给前端选择
--    - 执行开始时间通常默认采用活动开始时间
--    - 最大成功次数默认 5 次
--    - 不再强制要求结束时间，任务主要根据成功次数自动停止
-- =========================================================

USE `ibox`;

-- =========================================================
-- 表名: ibox_synthetic_task
-- 说明: 活动合成任务主表
-- =========================================================
DROP TABLE IF EXISTS `ibox_synthetic_task`;
CREATE TABLE `ibox_synthetic_task` (
                                       `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                       `task_name` VARCHAR(128) NOT NULL COMMENT '任务名称，允许前端留空并由后端自动生成10位编码',
                                       `activity_id` BIGINT NOT NULL COMMENT '活动ID',
                                       `activity_name` VARCHAR(128) DEFAULT NULL COMMENT '活动名称',
                                       `synthetic_id` BIGINT NOT NULL COMMENT '合成通道ID',
                                       `channel_name` VARCHAR(128) DEFAULT NULL COMMENT '通道名称',
                                       `account_ids` VARCHAR(512) NOT NULL COMMENT '账号ID列表，逗号分隔',
                                       `target_group_id` BIGINT DEFAULT NULL COMMENT '目标合成分组ID',
                                       `target_group_name` VARCHAR(128) DEFAULT NULL COMMENT '目标合成成品名称',
                                       `material_strategy` VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '材料策略',
                                       `material_album_ids` VARCHAR(512) DEFAULT NULL COMMENT '材料藏品ID列表',
                                       `max_synthetic_count` INT NOT NULL DEFAULT 5 COMMENT '最大成功次数，默认5次',
                                       `success_count` INT NOT NULL DEFAULT 0 COMMENT '已成功次数',
                                       `execute_start_time` DATETIME NOT NULL COMMENT '执行开始时间，通常默认使用活动开始时间',
                                       `execute_end_time` DATETIME DEFAULT NULL COMMENT '执行结束时间，可为空；当前主要按成功次数自动结束',
                                       `interval_millis` BIGINT NOT NULL DEFAULT 1000 COMMENT '循环间隔毫秒',
                                       `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态，0-禁用，1-启用',
                                       `running` TINYINT NOT NULL DEFAULT 0 COMMENT '是否运行中，0-否，1-是',
                                       `last_execute_time` DATETIME DEFAULT NULL COMMENT '最近一次执行时间',
                                       `last_result_code` VARCHAR(64) DEFAULT NULL COMMENT '最近一次结果码',
                                       `last_result_message` VARCHAR(255) DEFAULT NULL COMMENT '最近一次结果说明',
                                       `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
                                       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0-未删除，1-已删除',
                                       PRIMARY KEY (`id`),
                                       KEY `idx_synthetic_task_activity_id` (`activity_id`),
                                       KEY `idx_synthetic_task_synthetic_id` (`synthetic_id`),
                                       KEY `idx_synthetic_task_status` (`status`),
                                       KEY `idx_synthetic_task_running` (`running`),
                                       KEY `idx_synthetic_task_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='活动合成任务主表';

-- =========================================================
-- 表名: ibox_synthetic_task_record
-- 说明: 活动合成执行记录表
-- =========================================================
DROP TABLE IF EXISTS `ibox_synthetic_task_record`;
CREATE TABLE `ibox_synthetic_task_record` (
                                              `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                              `task_id` BIGINT NOT NULL COMMENT '任务ID',
                                              `account_id` BIGINT DEFAULT NULL COMMENT '账号ID',
                                              `activity_id` BIGINT DEFAULT NULL COMMENT '活动ID',
                                              `synthetic_id` BIGINT DEFAULT NULL COMMENT '合成通道ID',
                                              `request_payload` JSON DEFAULT NULL COMMENT '请求报文快照',
                                              `response_payload` JSON DEFAULT NULL COMMENT '响应报文快照',
                                              `result_code` VARCHAR(64) DEFAULT NULL COMMENT '结果码',
                                              `result_message` VARCHAR(255) DEFAULT NULL COMMENT '结果说明',
                                              `success_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否成功，0-否，1-是',
                                              `target_token_ids` VARCHAR(255) DEFAULT NULL COMMENT '返回的tokenId列表',
                                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                              `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                              `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0-未删除，1-已删除',
                                              PRIMARY KEY (`id`),
                                              KEY `idx_synthetic_task_record_task_id` (`task_id`),
                                              KEY `idx_synthetic_task_record_account_id` (`account_id`),
                                              KEY `idx_synthetic_task_record_success_flag` (`success_flag`),
                                              KEY `idx_synthetic_task_record_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='活动合成执行记录表';
