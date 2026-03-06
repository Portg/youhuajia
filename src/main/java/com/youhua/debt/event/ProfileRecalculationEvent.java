package com.youhua.debt.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 画像重新计算事件。
 * 当债务状态变更影响画像时，通过 Spring Event 触发（MVP 替代消息队列）。
 */
@Getter
public class ProfileRecalculationEvent extends ApplicationEvent {

    private final Long userId;
    private final Long debtId;
    private final String triggerReason;

    public ProfileRecalculationEvent(Object source, Long userId, Long debtId, String triggerReason) {
        super(source);
        this.userId = userId;
        this.debtId = debtId;
        this.triggerReason = triggerReason;
    }
}
