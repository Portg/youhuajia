package com.youhua.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优化报告：有 createTime 和 deleted，无 updateTime，不继承 BaseEntity。
 */
@Getter
@Setter
@TableName("t_optimization_report")
public class OptimizationReport implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String profileSnapshotJson;

    private String priorityListJson;

    private String actionPlanJson;

    private String aiSummary;

    private String riskWarnings;

    private String explainabilityJson;

    private Integer reportVersion;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
