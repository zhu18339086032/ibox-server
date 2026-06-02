ALTER TABLE `ibox_monitor_task_config`
    ADD COLUMN `account_ids` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '账号ID列表，逗号分隔' AFTER `monitor_type`;

UPDATE `ibox_monitor_task_config`
SET `account_ids` = CAST(`account_id` AS CHAR)
WHERE `account_id` IS NOT NULL
  AND (`account_ids` = '' OR `account_ids` IS NULL);

ALTER TABLE `ibox_monitor_task_config`
    DROP INDEX `idx_monitor_task_config_account_id`;

ALTER TABLE `ibox_monitor_task_config`
    DROP COLUMN `account_id`;
