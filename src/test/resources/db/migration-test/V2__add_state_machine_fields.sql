-- ============================================================
-- V2__add_state_machine_fields.sql (H2 test variant)
-- Removes MySQL-specific AFTER clause not supported by H2.
-- ============================================================

-- 补充 t_finance_profile 画像生成状态字段
ALTER TABLE t_finance_profile
    ADD COLUMN generation_status      VARCHAR(30)  NULL;
ALTER TABLE t_finance_profile
    ADD COLUMN generation_retry_count INT          NOT NULL DEFAULT 0;

-- 补充 t_user 注销申请时间字段
ALTER TABLE t_user
    ADD COLUMN cancellation_time DATETIME(3) NULL;
