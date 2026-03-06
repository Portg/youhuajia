-- ============================================================
-- V3__seed_test_data.sql — 测试 Seed 数据
-- 遵循: mock-data.md 5个用户画像 A-E
-- 插入顺序: t_user → t_income_record → t_debt → t_finance_profile
-- 金额: DECIMAL(18,4) | 比率: DECIMAL(10,6) | 时间: DATETIME(3)
-- 枚举: 存字符串值 | 手机号: 13800000001-5（加密占位）
-- ============================================================

-- ------------------------------------------------------------
-- 用户A：轻度负债白领（健康型）id=100001
-- 用户B：中度负债创业者（需关注型）id=100002
-- 用户C：重度负债个体户（高风险型）id=100003
-- 用户D：零利息特殊场景 id=100004
-- 用户E：仅草稿未确认（边界场景）id=100005
-- ------------------------------------------------------------

-- ============================================================
-- 1. t_user
-- ============================================================
INSERT INTO t_user
    (id, phone, phone_hash, nickname, status, create_time, update_time, deleted)
VALUES
    (100001, 'ENC_13800000001', SHA2('13800000001', 256), '小明',   'ACTIVE', '2026-01-01 10:00:00.000', '2026-01-01 10:00:00.000', 0),
    (100002, 'ENC_13800000002', SHA2('13800000002', 256), '老王',   'ACTIVE', '2026-01-05 10:00:00.000', '2026-01-05 10:00:00.000', 0),
    (100003, 'ENC_13800000003', SHA2('13800000003', 256), '阿强',   'ACTIVE', '2026-01-10 10:00:00.000', '2026-01-10 10:00:00.000', 0),
    (100004, 'ENC_13800000004', SHA2('13800000004', 256), '小李',   'ACTIVE', '2026-01-15 10:00:00.000', '2026-01-15 10:00:00.000', 0),
    (100005, 'ENC_13800000005', SHA2('13800000005', 256), '测试用户','ACTIVE', '2026-01-20 10:00:00.000', '2026-01-20 10:00:00.000', 0);

-- ============================================================
-- 2. t_income_record
-- ============================================================
INSERT INTO t_income_record
    (id, user_id, income_type, amount, `primary`, verification_status, create_time, update_time, deleted)
VALUES
    -- 用户A：工资 15000
    (200001, 100001, 'SALARY',    15000.0000, 1, 'UNVERIFIED', '2026-01-01 10:01:00.000', '2026-01-01 10:01:00.000', 0),

    -- 用户B：经营收入 20000（主）+ 自由职业 3000（副）
    (200002, 100002, 'BUSINESS',  20000.0000, 1, 'UNVERIFIED', '2026-01-05 10:01:00.000', '2026-01-05 10:01:00.000', 0),
    (200003, 100002, 'FREELANCE',  3000.0000, 0, 'UNVERIFIED', '2026-01-05 10:02:00.000', '2026-01-05 10:02:00.000', 0),

    -- 用户C：经营收入 12000
    (200004, 100003, 'BUSINESS',  12000.0000, 1, 'UNVERIFIED', '2026-01-10 10:01:00.000', '2026-01-10 10:01:00.000', 0),

    -- 用户D：工资 8000
    (200005, 100004, 'SALARY',     8000.0000, 1, 'UNVERIFIED', '2026-01-15 10:01:00.000', '2026-01-15 10:01:00.000', 0);

    -- 用户E：无收入记录

-- ============================================================
-- 3. t_debt
-- ============================================================

-- ---- 用户A：3笔债务（均 IN_PROFILE）----
INSERT INTO t_debt
    (id, user_id, creditor, debt_type, principal, total_repayment, loan_days,
     overdue_status, overdue_days, source_type, confidence_score, status,
     create_time, update_time, deleted, version)
VALUES
    -- A-1: 招商银行信用卡
    (300001, 100001, '招商银行信用卡', 'CREDIT_CARD',   30000.0000, 31500.0000, 365,
     'NORMAL', 0, 'MANUAL', NULL, 'IN_PROFILE',
     '2026-01-01 10:05:00.000', '2026-01-01 10:05:00.000', 0, 0),

    -- A-2: 花呗
    (300002, 100001, '花呗',          'CONSUMER_LOAN',  8000.0000,  8800.0000, 180,
     'NORMAL', 0, 'MANUAL', NULL, 'IN_PROFILE',
     '2026-01-01 10:06:00.000', '2026-01-01 10:06:00.000', 0, 0),

    -- A-3: 京东白条（OCR, confidence=88.5）
    (300003, 100001, '京东白条',       'CONSUMER_LOAN', 12000.0000, 13200.0000, 365,
     'NORMAL', 0, 'OCR', 88.50, 'IN_PROFILE',
     '2026-01-01 10:07:00.000', '2026-01-01 10:07:00.000', 0, 0);

-- ---- 用户B：3笔债务（均 IN_PROFILE，含 OVERDUE_30）----
INSERT INTO t_debt
    (id, user_id, creditor, debt_type, principal, total_repayment, loan_days,
     overdue_status, overdue_days, source_type, confidence_score, status,
     create_time, update_time, deleted, version)
VALUES
    -- B-1: 工商银行经营贷
    (300004, 100002, '工商银行经营贷', 'BUSINESS_LOAN', 200000.0000, 240000.0000, 730,
     'NORMAL', 0, 'MANUAL', NULL, 'IN_PROFILE',
     '2026-01-05 10:05:00.000', '2026-01-05 10:05:00.000', 0, 0),

    -- B-2: 微众银行（OCR, confidence=75.0）
    (300005, 100002, '微众银行',       'CONSUMER_LOAN',  50000.0000,  62000.0000, 365,
     'NORMAL', 0, 'OCR', 75.00, 'IN_PROFILE',
     '2026-01-05 10:06:00.000', '2026-01-05 10:06:00.000', 0, 0),

    -- B-3: 平安普惠（逾期30天，overdue_days=15）
    (300006, 100002, '平安普惠',       'CONSUMER_LOAN',  30000.0000,  39000.0000, 365,
     'OVERDUE_30', 15, 'MANUAL', NULL, 'IN_PROFILE',
     '2026-01-05 10:07:00.000', '2026-01-05 10:07:00.000', 0, 0);

-- ---- 用户C：7笔债务（均 IN_PROFILE，多种逾期）----
INSERT INTO t_debt
    (id, user_id, creditor, debt_type, principal, total_repayment, loan_days,
     overdue_status, overdue_days, source_type, confidence_score, status,
     create_time, update_time, deleted, version)
VALUES
    -- C-1: 网贷A（OVERDUE_60，45天）
    (300007, 100003, '某网贷平台A', 'CONSUMER_LOAN',  50000.0000,  72000.0000, 365,
     'OVERDUE_60', 45, 'MANUAL', NULL, 'IN_PROFILE',
     '2026-01-10 10:05:00.000', '2026-01-10 10:05:00.000', 0, 0),

    -- C-2: 网贷B（OVERDUE_90_PLUS，120天）
    (300008, 100003, '某网贷平台B', 'CONSUMER_LOAN',  80000.0000, 120000.0000, 365,
     'OVERDUE_90_PLUS', 120, 'MANUAL', NULL, 'IN_PROFILE',
     '2026-01-10 10:06:00.000', '2026-01-10 10:06:00.000', 0, 0),

    -- C-3: 信用卡A（OVERDUE_30，20天）
    (300009, 100003, '信用卡A',      'CREDIT_CARD',   60000.0000,  78000.0000, 365,
     'OVERDUE_30', 20, 'MANUAL', NULL, 'IN_PROFILE',
     '2026-01-10 10:07:00.000', '2026-01-10 10:07:00.000', 0, 0),

    -- C-4: 信用卡B（OCR, confidence=62.0，正常）
    (300010, 100003, '信用卡B',      'CREDIT_CARD',   40000.0000,  52000.0000, 365,
     'NORMAL', 0, 'OCR', 62.00, 'IN_PROFILE',
     '2026-01-10 10:08:00.000', '2026-01-10 10:08:00.000', 0, 0),

    -- C-5: 经营贷（正常）
    (300011, 100003, '经营贷',       'BUSINESS_LOAN', 150000.0000, 195000.0000, 365,
     'NORMAL', 0, 'MANUAL', NULL, 'IN_PROFILE',
     '2026-01-10 10:09:00.000', '2026-01-10 10:09:00.000', 0, 0),

    -- C-6: 亲友借款（零利息，OTHER）
    (300012, 100003, '亲友借款',      'OTHER',         100000.0000, 100000.0000, 365,
     'NORMAL', 0, 'MANUAL', NULL, 'IN_PROFILE',
     '2026-01-10 10:10:00.000', '2026-01-10 10:10:00.000', 0, 0),

    -- C-7: 某分期平台（180天）
    (300013, 100003, '某分期平台',    'CONSUMER_LOAN',  20000.0000,  28000.0000, 180,
     'NORMAL', 0, 'MANUAL', NULL, 'IN_PROFILE',
     '2026-01-10 10:11:00.000', '2026-01-10 10:11:00.000', 0, 0);

-- ---- 用户D：1笔债务（IN_PROFILE，零利息）----
INSERT INTO t_debt
    (id, user_id, creditor, debt_type, principal, total_repayment, loan_days,
     overdue_status, overdue_days, source_type, confidence_score, status,
     create_time, update_time, deleted, version)
VALUES
    (300014, 100004, '花呗免息分期', 'CONSUMER_LOAN', 6000.0000, 6000.0000, 90,
     'NORMAL', 0, 'MANUAL', NULL, 'IN_PROFILE',
     '2026-01-15 10:05:00.000', '2026-01-15 10:05:00.000', 0, 0);

-- ---- 用户E：1笔债务（DRAFT，边界场景）----
INSERT INTO t_debt
    (id, user_id, creditor, debt_type, principal, total_repayment, loan_days,
     overdue_status, overdue_days, source_type, confidence_score, status,
     create_time, update_time, deleted, version)
VALUES
    (300015, 100005, '测试机构', 'CONSUMER_LOAN', 10000.0000, 12000.0000, 365,
     'NORMAL', 0, 'MANUAL', NULL, 'DRAFT',
     '2026-01-20 10:05:00.000', '2026-01-20 10:05:00.000', 0, 0);

-- ============================================================
-- 4. t_finance_profile
-- 仅为已纳入画像的用户（A/B/C/D）生成画像，用户E无画像
-- score_detail_json 存储评分维度明细占位
-- highest_apr_debt_id 指向各用户APR最高的债务
-- ============================================================
INSERT INTO t_finance_profile
    (id, user_id, total_debt, debt_count, weighted_apr, monthly_payment,
     monthly_income, debt_income_ratio, restructure_score, risk_level,
     overdue_count, highest_apr_debt_id, score_detail_json,
     last_calculated_time, generation_status, generation_retry_count,
     create_time, update_time, deleted, version)
VALUES
    -- 画像A：LOW风险，重组评分82
    (400001, 100001,
     50000.0000, 3,
     8.440000,   -- weighted_apr (DECIMAL(10,6))
     3700.0000,
     15000.0000,
     0.246700,   -- debt_income_ratio
     82.00,      -- restructure_score
     'LOW',
     0,          -- overdue_count
     300001,     -- 招商银行信用卡 APR最高
     '{"liquidityScore":75.0,"overdueScore":100.0,"debtRatioScore":80.0}',
     '2026-01-01 10:30:00.000',
     'COMPLETED', 0,
     '2026-01-01 10:30:00.000', '2026-01-01 10:30:00.000', 0, 0),

    -- 画像B：MEDIUM风险，重组评分62
    (400002, 100002,
     280000.0000, 3,
     16.570000,
     12200.0000,
     23000.0000,
     0.530400,
     62.00,
     'MEDIUM',
     1,          -- overdue_count（平安普惠逾期）
     300006,     -- 平安普惠 APR最高
     '{"liquidityScore":55.0,"overdueScore":60.0,"debtRatioScore":50.0}',
     '2026-01-05 10:30:00.000',
     'COMPLETED', 0,
     '2026-01-05 10:30:00.000', '2026-01-05 10:30:00.000', 0, 0),

    -- 画像C：CRITICAL风险，重组评分22
    (400003, 100003,
     500000.0000, 7,
     29.000000,
     18500.0000,
     12000.0000,
     1.541700,
     22.00,
     'CRITICAL',
     3,          -- overdue_count（网贷A/B/信用卡A逾期）
     300008,     -- 网贷B APR最高
     '{"liquidityScore":20.0,"overdueScore":10.0,"debtRatioScore":5.0}',
     '2026-01-10 10:30:00.000',
     'COMPLETED', 0,
     '2026-01-10 10:30:00.000', '2026-01-10 10:30:00.000', 0, 0),

    -- 画像D：LOW风险，零利息，重组评分88
    (400004, 100004,
     6000.0000, 1,
     0.000000,
     2000.0000,
     8000.0000,
     0.250000,
     88.00,
     'LOW',
     0,
     300014,     -- 花呗免息分期（唯一债务）
     '{"liquidityScore":90.0,"overdueScore":100.0,"debtRatioScore":85.0}',
     '2026-01-15 10:30:00.000',
     'COMPLETED', 0,
     '2026-01-15 10:30:00.000', '2026-01-15 10:30:00.000', 0, 0);
