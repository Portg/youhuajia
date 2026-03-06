-- ============================================================
-- V6__backfill_debt_apr.sql — 补充种子债务的 APR 字段
-- APR = (totalRepayment - principal) / principal × (365 / loanDays) × 100
-- 精度: DECIMAL(10,6)
-- ============================================================

-- 用户A (100001)
-- A-1: 招商银行信用卡: (31500-30000)/30000 × 365/365 × 100 = 5.000000
UPDATE t_debt SET apr = 5.000000 WHERE id = 300001 AND apr IS NULL;
-- A-2: 花呗: (8800-8000)/8000 × 365/180 × 100 = 20.277778
UPDATE t_debt SET apr = 20.277778 WHERE id = 300002 AND apr IS NULL;
-- A-3: 京东白条: (13200-12000)/12000 × 365/365 × 100 = 10.000000
UPDATE t_debt SET apr = 10.000000 WHERE id = 300003 AND apr IS NULL;

-- 用户B (100002)
-- B-1: 工商银行经营贷: (240000-200000)/200000 × 365/730 × 100 = 10.000000
UPDATE t_debt SET apr = 10.000000 WHERE id = 300004 AND apr IS NULL;
-- B-2: 微众银行: (62000-50000)/50000 × 365/365 × 100 = 24.000000
UPDATE t_debt SET apr = 24.000000 WHERE id = 300005 AND apr IS NULL;
-- B-3: 平安普惠: (39000-30000)/30000 × 365/365 × 100 = 30.000000
UPDATE t_debt SET apr = 30.000000 WHERE id = 300006 AND apr IS NULL;

-- 用户C (100003)
-- C-1: 网贷A: (72000-50000)/50000 × 365/365 × 100 = 44.000000
UPDATE t_debt SET apr = 44.000000 WHERE id = 300007 AND apr IS NULL;
-- C-2: 网贷B: (120000-80000)/80000 × 365/365 × 100 = 50.000000
UPDATE t_debt SET apr = 50.000000 WHERE id = 300008 AND apr IS NULL;
-- C-3: 信用卡A: (78000-60000)/60000 × 365/365 × 100 = 30.000000
UPDATE t_debt SET apr = 30.000000 WHERE id = 300009 AND apr IS NULL;
-- C-4: 信用卡B: (52000-40000)/40000 × 365/365 × 100 = 30.000000
UPDATE t_debt SET apr = 30.000000 WHERE id = 300010 AND apr IS NULL;
-- C-5: 经营贷: (195000-150000)/150000 × 365/365 × 100 = 30.000000
UPDATE t_debt SET apr = 30.000000 WHERE id = 300011 AND apr IS NULL;
-- C-6: 亲友借款: (100000-100000)/100000 × 365/365 × 100 = 0.000000
UPDATE t_debt SET apr = 0.000000 WHERE id = 300012 AND apr IS NULL;
-- C-7: 某分期平台: (28000-20000)/20000 × 365/180 × 100 = 81.111111
UPDATE t_debt SET apr = 81.111111 WHERE id = 300013 AND apr IS NULL;

-- 用户D (100004)
-- D-1: 花呗免息分期: (6000-6000)/6000 × 365/90 × 100 = 0.000000
UPDATE t_debt SET apr = 0.000000 WHERE id = 300014 AND apr IS NULL;

-- 用户E (100005) - DRAFT 状态
-- E-1: 测试机构: (12000-10000)/10000 × 365/365 × 100 = 20.000000
UPDATE t_debt SET apr = 20.000000 WHERE id = 300015 AND apr IS NULL;
