-- Score record table for tracking scoring results and strategy effectiveness
CREATE TABLE t_score_record (
    id              BIGINT          NOT NULL COMMENT '雪花 ID',
    user_id         BIGINT          NOT NULL COMMENT '用户 ID',
    strategy_name   VARCHAR(100)    NOT NULL COMMENT '使用的策略名',
    strategy_version VARCHAR(50)    DEFAULT NULL COMMENT '策略版本',
    segment         VARCHAR(20)     NOT NULL COMMENT '命中的用户分群',
    final_score     DECIMAL(5,2)    NOT NULL COMMENT '最终得分 0-100',
    risk_level      VARCHAR(20)     NOT NULL COMMENT '风险等级',
    recommendation  VARCHAR(30)     NOT NULL COMMENT '推荐路径',
    dimension_scores_json JSON      DEFAULT NULL COMMENT '各维度 partialScore',
    reason_codes_json     JSON      DEFAULT NULL COMMENT 'ReasonCode1~3',
    input_snapshot_json   JSON      DEFAULT NULL COMMENT 'ScoreInput 快照',
    score_delta     DECIMAL(5,2)    DEFAULT NULL COMMENT '与该用户上次评分的差值',
    create_time     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_score_record_user_id (user_id),
    INDEX idx_score_record_strategy (strategy_name, strategy_version),
    INDEX idx_score_record_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评分记录表';
