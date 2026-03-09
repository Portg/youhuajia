-- V12: 补充 t_finance_profile 的逾期天数和房贷笔数字段
-- max_overdue_days: 最大逾期天数（前端 scoreSimulator 评分用）
-- mortgage_count:   房贷笔数（前端分群匹配 MORTGAGE_HEAVY 用）
ALTER TABLE t_finance_profile
    ADD COLUMN max_overdue_days INT NOT NULL DEFAULT 0 COMMENT '最大逾期天数' AFTER overdue_count,
    ADD COLUMN mortgage_count   INT NOT NULL DEFAULT 0 COMMENT '房贷笔数'     AFTER max_overdue_days;
