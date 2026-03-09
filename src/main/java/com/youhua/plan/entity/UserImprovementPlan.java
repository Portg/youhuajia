package com.youhua.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_improvement_plan")
public class UserImprovementPlan {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Boolean layer1Completed;

    /** Layer1 生成的报告 ID，nullable */
    private Long layer1ReportId;

    private Boolean layer2Completed;

    private Boolean layer3Completed;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Boolean deleted;

    @Version
    private Integer version;
}
