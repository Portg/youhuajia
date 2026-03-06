-- ============================================================
-- V7__add_consultation_request_table.sql — 咨询意向收集表
-- ============================================================

CREATE TABLE t_consultation_request (
    id              BIGINT          NOT NULL COMMENT '雪花ID',
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    phone           VARCHAR(20)     NOT NULL COMMENT '联系手机号',
    consult_type    VARCHAR(32)     NOT NULL COMMENT '咨询类型: DEBT_OPTIMIZATION / RATE_NEGOTIATION / GENERAL',
    remark          VARCHAR(500)    NULL     COMMENT '补充说明',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING / CONTACTED / CLOSED',
    create_time     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_consultation_request_user_id (user_id),
    INDEX idx_consultation_request_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='咨询意向收集';
