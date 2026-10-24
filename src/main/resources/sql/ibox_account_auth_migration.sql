-- 已有数据库执行一次即可；新库请使用 ibox_server.sql 中的完整表结构。
ALTER TABLE ibox_account
    ADD COLUMN refresh_token TEXT NULL COMMENT '刷新令牌',
    ADD COLUMN token_expired_at BIGINT NULL COMMENT '访问令牌过期时间（毫秒时间戳）',
    ADD COLUMN refresh_expired_at BIGINT NULL COMMENT '刷新令牌过期时间（毫秒时间戳）';
