-- V8: Add extra computed fields to t_finance_profile for Page4/Page6/Page9 display
ALTER TABLE t_finance_profile
    ADD COLUMN three_year_extra_interest DECIMAL(18,4) NULL COMMENT '3年多付利息（相对市场基准利率）' AFTER highest_apr_debt_id,
    ADD COLUMN avg_loan_days             INT           NULL COMMENT '平均贷款天数'                      AFTER three_year_extra_interest,
    ADD COLUMN highest_apr_creditor      VARCHAR(100)  NULL COMMENT '最高利率债权人名称'               AFTER avg_loan_days;
