package com.youhua.engine.scoring.record;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Score record entity for tracking scoring results and strategy effectiveness.
 */
@Data
@TableName("t_score_record")
public class ScoreRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String strategyName;

    private String strategyVersion;

    private String segment;

    private BigDecimal finalScore;

    private String riskLevel;

    private String recommendation;

    private String dimensionScoresJson;

    private String reasonCodesJson;

    private String inputSnapshotJson;

    private BigDecimal scoreDelta;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
