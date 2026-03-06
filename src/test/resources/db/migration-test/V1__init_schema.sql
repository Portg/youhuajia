-- ============================================================
-- V1__init_schema.sql — MVP 初始化 DDL
-- 遵循: entities.md + evolution.md
-- 字符集: utf8mb4 | 引擎: InnoDB | 时间: DATETIME(3)
-- 金额: DECIMAL(18,4) | 比率: DECIMAL(10,6)
-- ============================================================

-- ------------------------------------------------------------
-- 1. t_user 用户表
-- ------------------------------------------------------------
CREATE TABLE t_user (
    id                  BIGINT          NOT NULL            COMMENT '用户ID（雪花算法）',
    phone               VARCHAR(20)     NOT NULL            COMMENT '手机号（加密存储）',
    phone_hash          VARCHAR(64)     NOT NULL            COMMENT '手机号哈希（用于查询）',
    nickname            VARCHAR(50)     NULL                COMMENT '昵称',
    avatar_url          VARCHAR(256)    NULL                COMMENT '头像地址',
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/FROZEN/CANCELLING/CANCELLED',
    last_login_time     DATETIME(3)     NULL                COMMENT '最后登录时间',
    last_login_ip       VARCHAR(45)     NULL                COMMENT '最后登录IP',
    device_fingerprint  VARCHAR(128)    NULL                COMMENT '设备指纹',
    family_id           BIGINT          NULL                COMMENT '家庭ID（V2.1预留）',
    member_level        VARCHAR(20)     NULL                COMMENT '会员等级（V2.1预留）',
    growth_value        INT             NOT NULL DEFAULT 0  COMMENT '成长值（V2.1预留）',
    digital_wallet_id   VARCHAR(64)     NULL                COMMENT '数字人民币钱包ID（V2.0预留）',
    create_time         DATETIME(3)     NOT NULL            COMMENT '创建时间',
    update_time         DATETIME(3)     NOT NULL            COMMENT '更新时间',
    deleted             TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除 0-否 1-是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone_hash (phone_hash),
    KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. t_debt 债务表
-- ------------------------------------------------------------
CREATE TABLE t_debt (
    id                  BIGINT          NOT NULL            COMMENT '债务ID',
    user_id             BIGINT          NOT NULL            COMMENT '用户ID',
    creditor            VARCHAR(100)    NOT NULL            COMMENT '债权机构名称',
    debt_type           VARCHAR(30)     NOT NULL            COMMENT 'CREDIT_CARD/CONSUMER_LOAN/BUSINESS_LOAN/MORTGAGE/OTHER',
    principal           DECIMAL(18,4)   NOT NULL            COMMENT '借款本金',
    total_repayment     DECIMAL(18,4)   NOT NULL            COMMENT '总还款额（含利息+费用）',
    nominal_rate        DECIMAL(10,6)   NULL                COMMENT '名义利率（合同标注）',
    apr                 DECIMAL(10,6)   NULL                COMMENT '实际年化利率（系统计算）',
    loan_days           INT             NOT NULL            COMMENT '借款天数',
    monthly_payment     DECIMAL(18,4)   NULL                COMMENT '月供金额',
    remaining_principal DECIMAL(18,4)   NULL                COMMENT '剩余本金',
    remaining_periods   INT             NULL                COMMENT '剩余期数',
    start_date          DATE            NULL                COMMENT '借款起始日',
    end_date            DATE            NULL                COMMENT '借款到期日',
    overdue_status      VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/OVERDUE_30/OVERDUE_60/OVERDUE_90_PLUS',
    overdue_days        INT             NOT NULL DEFAULT 0  COMMENT '逾期天数',
    source_type         VARCHAR(20)     NOT NULL            COMMENT 'MANUAL/OCR/BANK_API',
    ocr_task_id         BIGINT          NULL                COMMENT 'OCR任务ID',
    confidence_score    DECIMAL(5,2)    NULL                COMMENT 'OCR识别置信度（0-100）',
    status              VARCHAR(20)     NOT NULL            COMMENT 'DRAFT/SUBMITTED/OCR_PROCESSING/PENDING_CONFIRM/CONFIRMED/IN_PROFILE',
    remark              VARCHAR(500)    NULL                COMMENT '备注',
    family_id           BIGINT          NULL                COMMENT '归属家庭ID（V2.1预留）',
    bank_sync_id        VARCHAR(64)     NULL                COMMENT '银行同步关联ID（V2.0预留）',
    create_time         DATETIME(3)     NOT NULL            COMMENT '创建时间',
    update_time         DATETIME(3)     NOT NULL            COMMENT '更新时间',
    deleted             TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0  COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_debt_user_id (user_id),
    KEY idx_debt_user_status (user_id, status),
    KEY idx_debt_creditor (creditor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='债务表';

-- ------------------------------------------------------------
-- 3. t_finance_profile 财务画像表
-- ------------------------------------------------------------
CREATE TABLE t_finance_profile (
    id                    BIGINT          NOT NULL            COMMENT '画像ID',
    user_id               BIGINT          NOT NULL            COMMENT '用户ID（一对一）',
    total_debt            DECIMAL(18,4)   NOT NULL            COMMENT '总负债',
    debt_count            INT             NOT NULL            COMMENT '债务笔数',
    weighted_apr          DECIMAL(10,6)   NOT NULL            COMMENT '加权年化利率',
    monthly_payment       DECIMAL(18,4)   NOT NULL            COMMENT '月供总额',
    monthly_income        DECIMAL(18,4)   NULL                COMMENT '月收入（用户填写）',
    debt_income_ratio     DECIMAL(10,6)   NULL                COMMENT '负债收入比',
    liquidity_score       DECIMAL(5,2)    NULL                COMMENT '资产流动性评分（0-100）',
    restructure_score     DECIMAL(5,2)    NULL                COMMENT '重组可行性评分（0-100）',
    risk_level            VARCHAR(20)     NULL                COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
    overdue_count         INT             NOT NULL DEFAULT 0  COMMENT '逾期笔数',
    highest_apr_debt_id   BIGINT          NULL                COMMENT '最高利率债务ID',
    score_detail_json     JSON            NULL                COMMENT '评分维度明细',
    last_calculated_time  DATETIME(3)     NULL                COMMENT '最近一次计算时间',
    asset_total           DECIMAL(18,4)   NULL                COMMENT '总资产（V2.0预留）',
    net_worth             DECIMAL(18,4)   NULL                COMMENT '净资产（V2.0预留）',
    family_profile_id     BIGINT          NULL                COMMENT '家庭画像ID（V2.1预留）',
    create_time           DATETIME(3)     NOT NULL            COMMENT '创建时间',
    update_time           DATETIME(3)     NOT NULL            COMMENT '更新时间',
    deleted               TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    version               INT             NOT NULL DEFAULT 0  COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_profile_user_id (user_id),
    KEY idx_profile_risk_level (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='财务画像表';

-- ------------------------------------------------------------
-- 4. t_income_record 收入记录表
-- ------------------------------------------------------------
CREATE TABLE t_income_record (
    id                    BIGINT          NOT NULL            COMMENT '记录ID',
    user_id               BIGINT          NOT NULL            COMMENT '用户ID',
    income_type           VARCHAR(30)     NOT NULL            COMMENT 'SALARY/BUSINESS/FREELANCE/INVESTMENT/OTHER',
    amount                DECIMAL(18,4)   NOT NULL            COMMENT '月收入金额',
    `primary`             TINYINT         NOT NULL DEFAULT 0  COMMENT '是否主要收入来源',
    verification_status   VARCHAR(20)     NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED/VERIFIED',
    create_time           DATETIME(3)     NOT NULL            COMMENT '创建时间',
    update_time           DATETIME(3)     NOT NULL            COMMENT '更新时间',
    deleted               TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_income_record_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收入记录表';

-- ------------------------------------------------------------
-- 5. t_ocr_task OCR识别任务表
-- ------------------------------------------------------------
CREATE TABLE t_ocr_task (
    id                    BIGINT          NOT NULL            COMMENT '任务ID',
    user_id               BIGINT          NOT NULL            COMMENT '用户ID',
    file_url              VARCHAR(512)    NOT NULL            COMMENT '上传文件地址（MinIO）',
    file_type             VARCHAR(20)     NOT NULL            COMMENT 'CONTRACT/BILL/SMS_SCREENSHOT',
    status                VARCHAR(20)     NOT NULL            COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED',
    raw_result_json       JSON            NULL                COMMENT '原始识别结果',
    extracted_fields_json JSON            NULL                COMMENT '提取的结构化字段',
    confidence_score      DECIMAL(5,2)    NULL                COMMENT '整体置信度',
    error_message         VARCHAR(500)    NULL                COMMENT '失败原因',
    retry_count           INT             NOT NULL DEFAULT 0  COMMENT '重试次数',
    debt_id               BIGINT          NULL                COMMENT '关联的债务ID（确认后生成）',
    create_time           DATETIME(3)     NOT NULL            COMMENT '创建时间',
    update_time           DATETIME(3)     NOT NULL            COMMENT '更新时间',
    deleted               TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_ocr_task_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OCR识别任务表';

-- ------------------------------------------------------------
-- 6. t_operation_log 操作日志表（只追加不修改不删除）
-- ------------------------------------------------------------
CREATE TABLE t_operation_log (
    id                    BIGINT          NOT NULL            COMMENT '日志ID',
    user_id               BIGINT          NOT NULL            COMMENT '用户ID',
    module                VARCHAR(30)     NOT NULL            COMMENT 'DEBT/PROFILE/ENGINE/AI/AUTH',
    action                VARCHAR(50)     NOT NULL            COMMENT 'CREATE/UPDATE/DELETE/CALCULATE/GENERATE',
    target_type           VARCHAR(30)     NULL                COMMENT '操作对象类型',
    target_id             BIGINT          NULL                COMMENT '操作对象ID',
    detail_json           JSON            NULL                COMMENT '操作详情',
    ip                    VARCHAR(45)     NULL                COMMENT '操作IP',
    user_agent            VARCHAR(256)    NULL                COMMENT 'User-Agent',
    create_time           DATETIME(3)     NOT NULL            COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_operation_log_user_id (user_id),
    KEY idx_operation_log_module_action (module, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表（只追加）';

-- ------------------------------------------------------------
-- 7. t_optimization_report 优化报告表
-- ------------------------------------------------------------
CREATE TABLE t_optimization_report (
    id                      BIGINT          NOT NULL            COMMENT '报告ID',
    user_id                 BIGINT          NOT NULL            COMMENT '用户ID',
    profile_snapshot_json   JSON            NOT NULL            COMMENT '生成时的画像快照',
    priority_list_json      JSON            NOT NULL            COMMENT '优化优先级排序',
    action_plan_json        JSON            NOT NULL            COMMENT '90天行动计划',
    ai_summary              TEXT            NULL                COMMENT 'AI生成的文字建议',
    risk_warnings           JSON            NULL                COMMENT '风险提示列表',
    explainability_json     JSON            NULL                COMMENT '可解释性数据',
    report_version          INT             NOT NULL DEFAULT 1  COMMENT '报告版本（同一用户多次测评）',
    create_time             DATETIME(3)     NOT NULL            COMMENT '创建时间',
    deleted                 TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_optimization_report_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优化报告表';


-- ============================================================
-- V2.0/V2.1 预留表（MVP 建空结构，不写业务逻辑）
-- ============================================================

-- ------------------------------------------------------------
-- V2.0 资产账户表
-- ------------------------------------------------------------
CREATE TABLE t_asset_account (
    id                  BIGINT          NOT NULL            COMMENT '资产账户ID',
    user_id             BIGINT          NOT NULL            COMMENT '用户ID',
    account_type        VARCHAR(30)     NOT NULL            COMMENT 'BANK_CARD/FUND/HOUSE/VEHICLE/DIGITAL',
    account_name        VARCHAR(100)    NULL                COMMENT '账户名称',
    estimated_value     DECIMAL(18,4)   NULL                COMMENT '估值',
    create_time         DATETIME(3)     NOT NULL            COMMENT '创建时间',
    update_time         DATETIME(3)     NOT NULL            COMMENT '更新时间',
    deleted             TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0  COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_asset_account_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产账户表（V2.0启用）';

-- ------------------------------------------------------------
-- V2.0 重组申请表
-- ------------------------------------------------------------
CREATE TABLE t_restructure_application (
    id                  BIGINT          NOT NULL            COMMENT '申请ID',
    user_id             BIGINT          NOT NULL            COMMENT '用户ID',
    status              VARCHAR(30)     NOT NULL            COMMENT '申请状态',
    create_time         DATETIME(3)     NOT NULL            COMMENT '创建时间',
    update_time         DATETIME(3)     NOT NULL            COMMENT '更新时间',
    deleted             TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0  COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_restructure_application_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='重组申请表（V2.0启用）';

-- ------------------------------------------------------------
-- V2.1 家庭表
-- ------------------------------------------------------------
CREATE TABLE t_family (
    id                  BIGINT          NOT NULL            COMMENT '家庭ID',
    family_name         VARCHAR(50)     NULL                COMMENT '家庭名称',
    owner_user_id       BIGINT          NOT NULL            COMMENT '创建人用户ID',
    invite_code         VARCHAR(20)     NULL                COMMENT '邀请码',
    create_time         DATETIME(3)     NOT NULL            COMMENT '创建时间',
    update_time         DATETIME(3)     NOT NULL            COMMENT '更新时间',
    deleted             TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_family_invite_code (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家庭表（V2.1启用）';

-- ------------------------------------------------------------
-- V2.1 家庭成员表
-- ------------------------------------------------------------
CREATE TABLE t_family_member (
    id                  BIGINT          NOT NULL            COMMENT '成员ID',
    family_id           BIGINT          NOT NULL            COMMENT '家庭ID',
    user_id             BIGINT          NOT NULL            COMMENT '用户ID',
    role                VARCHAR(20)     NOT NULL            COMMENT 'OWNER/ADMIN/VIEWER',
    create_time         DATETIME(3)     NOT NULL            COMMENT '创建时间',
    update_time         DATETIME(3)     NOT NULL            COMMENT '更新时间',
    deleted             TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_family_member_family_id (family_id),
    KEY idx_family_member_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家庭成员表（V2.1启用）';
