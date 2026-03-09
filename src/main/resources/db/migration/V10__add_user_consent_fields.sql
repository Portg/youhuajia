-- 隐私协议同意记录（AG-13: 未同意不允许登录）
ALTER TABLE t_user
    ADD COLUMN consent_time    DATETIME(3)  NULL COMMENT '隐私协议同意时间',
    ADD COLUMN consent_version VARCHAR(16)  NULL COMMENT '同意的协议版本号';
