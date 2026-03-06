package com.youhua.infra.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 死信事件实体——记录异步事件处理失败的记录，供重试调度器使用。
 *
 * <p>不继承 BaseEntity，因为该表只需 create_time，无需 update_time / version。
 */
@Getter
@Setter
@TableName("t_failed_event")
public class FailedEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 事件类型全限定类名，例如 com.youhua.debt.event.ProfileRecalculationEvent */
    private String eventType;

    /** 事件数据 JSON 序列化 */
    private String payload;

    /** 最近一次错误信息（截断至 500 字符） */
    private String errorMessage;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次允许重试的时间 */
    private LocalDateTime nextRetryTime;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
