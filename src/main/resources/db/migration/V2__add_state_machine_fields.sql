-- ============================================================
-- V2__add_state_machine_fields.sql — 状态机相关字段补充
-- ============================================================

-- 补充 t_finance_profile 画像生成状态字段
ALTER TABLE t_finance_profile
    ADD COLUMN generation_status      VARCHAR(30)  NULL COMMENT 'IDLE/VALIDATING/CALCULATING/GENERATING_SUGGESTION/COMPLETED/COMPLETED_WITHOUT_AI/FAILED' AFTER last_calculated_time,
    ADD COLUMN generation_retry_count INT          NOT NULL DEFAULT 0 COMMENT '画像生成重试次数' AFTER generation_status;

-- 补充 t_user 注销申请时间字段
ALTER TABLE t_user
    ADD COLUMN cancellation_time DATETIME(3) NULL COMMENT '申请注销时间（用于30天冷静期判断）' AFTER status;
