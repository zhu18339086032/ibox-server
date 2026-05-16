-- =========================================================
-- ibox-server 初始化脚本
-- 数据库: MySQL 8.x
-- 字符集: utf8mb4
-- =========================================================

CREATE DATABASE IF NOT EXISTS `ibox`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `ibox`;

-- =========================================================
-- 表名: ibox_account
-- 说明: iBox账号表
-- =========================================================
DROP TABLE IF EXISTS `ibox_account`;
CREATE TABLE `ibox_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `account_name` VARCHAR(64) NOT NULL COMMENT '账号名称',
    `ibox_uid` BIGINT NOT NULL COMMENT 'iBox用户ID',
    `phone` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    `bearer_token` TEXT COMMENT 'Bearer访问令牌',
    `default_payment_platform_code` INT DEFAULT NULL COMMENT '默认支付平台编码',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ibox_account_uid` (`ibox_uid`),
    KEY `idx_ibox_account_name` (`account_name`),
    KEY `idx_ibox_account_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='iBox账号表';

-- =========================================================
-- 表名: ibox_market_collection
-- 说明: 市场藏品表
-- =========================================================
DROP TABLE IF EXISTS `ibox_market_collection`;
CREATE TABLE `ibox_market_collection` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `name` VARCHAR(128) NOT NULL COMMENT '藏品名称',
    `cover_pic_url` VARCHAR(512) DEFAULT NULL COMMENT '封面图地址',
    `file_url` VARCHAR(512) DEFAULT NULL COMMENT '资源文件地址',
    `issue_num` BIGINT DEFAULT 0 COMMENT '发行总量',
    `circulation_num` BIGINT DEFAULT 0 COMMENT '流通数量',
    `floor_price` DECIMAL(10,2) DEFAULT 0.00 COMMENT '地板价',
    `trade_volume` BIGINT DEFAULT 0 COMMENT '成交量',
    `focus` INT DEFAULT 0 COMMENT '是否关注',
    `status` INT DEFAULT 1 COMMENT '状态',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_market_collection_name` (`name`),
    KEY `idx_market_collection_status` (`status`),
    KEY `idx_market_collection_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='市场藏品表';

-- =========================================================
-- 表名: ibox_purchase_task
-- 说明: 抢购任务表
-- =========================================================
DROP TABLE IF EXISTS `ibox_purchase_task`;
CREATE TABLE `ibox_purchase_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
    `digital_collection_group_id` BIGINT NOT NULL COMMENT '商品ID',
    `collection_name` VARCHAR(128) DEFAULT NULL COMMENT '商品名称',
    `account_id` BIGINT NOT NULL COMMENT '账号ID',
    `max_count` INT NOT NULL DEFAULT 1 COMMENT '最大购买数量',
    `max_single_price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '最大单价',
    `interval_millis` BIGINT NOT NULL DEFAULT 100 COMMENT '间隔频率（毫秒）',
    `payment_platform_code` INT NOT NULL COMMENT '支付平台编码',
    `task_type` INT NOT NULL COMMENT '任务类型，1-单个下单，2-批量下单',
    `status` INT NOT NULL DEFAULT 1 COMMENT '任务状态，0-禁用，1-启用',
    `running` TINYINT NOT NULL DEFAULT 0 COMMENT '是否运行中，0-否，1-是',
    `notify_feishu` TINYINT NOT NULL DEFAULT 0 COMMENT '是否开启飞书通知，0-否，1-是',
    `feishu_webhook` VARCHAR(512) DEFAULT NULL COMMENT '飞书机器人Webhook地址',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_purchase_task_account_id` (`account_id`),
    KEY `idx_purchase_task_group_id` (`digital_collection_group_id`),
    KEY `idx_purchase_task_status` (`status`),
    KEY `idx_purchase_task_running` (`running`),
    KEY `idx_purchase_task_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='抢购任务表';

-- =========================================================
-- 表名: ibox_purchase_order
-- 说明: 抢购订单表
-- =========================================================
DROP TABLE IF EXISTS `ibox_purchase_order`;
CREATE TABLE `ibox_purchase_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `account_id` BIGINT NOT NULL COMMENT '账号ID',
    `digital_collection_id` BIGINT NOT NULL COMMENT '数字藏品ID',
    `ibox_order_id` VARCHAR(64) DEFAULT NULL COMMENT 'iBox返回的订单ID',
    `special_order_type` INT DEFAULT NULL COMMENT '特殊订单类型',
    `request_type` INT NOT NULL COMMENT '请求类型，1-单个下单，2-批量下单',
    `request_payload` JSON DEFAULT NULL COMMENT '请求报文',
    `response_payload` JSON DEFAULT NULL COMMENT '响应报文',
    `status` INT NOT NULL DEFAULT 0 COMMENT '处理状态',
    `submitted_at` DATETIME DEFAULT NULL COMMENT '提交时间',
    `finished_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_purchase_order_ibox_order_id` (`ibox_order_id`),
    KEY `idx_purchase_order_task_id` (`task_id`),
    KEY `idx_purchase_order_account_id` (`account_id`),
    KEY `idx_purchase_order_dc_id` (`digital_collection_id`),
    KEY `idx_purchase_order_status` (`status`),
    KEY `idx_purchase_order_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='抢购订单表';

-- =========================================================
-- 表名: ibox_monitor_task_config
-- 说明: 监控任务配置表
-- =========================================================
DROP TABLE IF EXISTS `ibox_monitor_task_config`;
CREATE TABLE `ibox_monitor_task_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
    `monitor_type` VARCHAR(32) NOT NULL COMMENT '监控类型',
    `account_id` BIGINT NOT NULL COMMENT '账号ID',
    `max_count` INT NOT NULL DEFAULT 1 COMMENT '最大数量',
    `max_price` DECIMAL(10,2) DEFAULT NULL COMMENT '最大价格',
    `status` INT NOT NULL DEFAULT 1 COMMENT '状态',
    `payment_platform_code` INT DEFAULT NULL COMMENT '支付平台编码',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_monitor_task_config_type` (`monitor_type`),
    KEY `idx_monitor_task_config_account_id` (`account_id`),
    KEY `idx_monitor_task_config_status` (`status`),
    KEY `idx_monitor_task_config_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监控任务配置表';
