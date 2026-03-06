-- V9: Add high_interest_debt_count to t_finance_profile for Page4 display
ALTER TABLE t_finance_profile
    ADD COLUMN high_interest_debt_count INT NULL DEFAULT NULL COMMENT 'APR > 24% 的高息债务笔数' AFTER highest_apr_creditor;
