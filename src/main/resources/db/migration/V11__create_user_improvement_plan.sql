-- 用户改善计划完成状态（AG-09：upsert 不保留历史，user_id 唯一索引）
CREATE TABLE t_user_improvement_plan
(
    id               BIGINT      NOT NULL COMMENT '雪花 ID',
    user_id          BIGINT      NOT NULL COMMENT '用户 ID',
    layer1_completed TINYINT(1)  NOT NULL DEFAULT 0 COMMENT 'Layer1 已完成',
    layer1_report_id BIGINT               DEFAULT NULL COMMENT 'Layer1 关联报告 ID',
    layer2_completed TINYINT(1)  NOT NULL DEFAULT 0 COMMENT 'Layer2 已完成',
    layer3_completed TINYINT(1)  NOT NULL DEFAULT 0 COMMENT 'Layer3 已完成',
    create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          TINYINT(1)  NOT NULL DEFAULT 0,
    version          INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_improvement_plan_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户改善计划完成状态';
