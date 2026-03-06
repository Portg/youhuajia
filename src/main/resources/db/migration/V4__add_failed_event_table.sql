-- V4: Add t_failed_event table for dead-letter event retry
CREATE TABLE t_failed_event (
    id              BIGINT          NOT NULL COMMENT '雪花算法主键',
    event_type      VARCHAR(100)    NOT NULL COMMENT '事件类型全限定类名',
    payload         JSON            NOT NULL COMMENT '事件数据',
    error_message   VARCHAR(500)    COMMENT '最近一次错误信息',
    retry_count     INT             NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_time DATETIME(3)     NOT NULL COMMENT '下次重试时间',
    create_time     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
    PRIMARY KEY (id),
    INDEX idx_t_failed_event_next_retry_time (next_retry_time),
    INDEX idx_t_failed_event_deleted_retry (deleted, retry_count, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='死信事件表（用于异步事件失败重试）';
