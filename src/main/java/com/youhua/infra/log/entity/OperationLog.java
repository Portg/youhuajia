package com.youhua.infra.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youhua.infra.log.enums.OperationAction;
import com.youhua.infra.log.enums.OperationModule;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志：只追加不修改不删除，不继承 BaseEntity。
 */
@Getter
@Setter
@TableName("t_operation_log")
public class OperationLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private OperationModule module;

    private OperationAction action;

    private String targetType;

    private Long targetId;

    private String detailJson;

    private String ip;

    private String userAgent;

    private LocalDateTime createTime;
}
