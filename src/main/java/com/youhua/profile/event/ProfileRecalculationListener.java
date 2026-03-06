package com.youhua.profile.event;

import com.youhua.debt.event.ProfileRecalculationEvent;
import com.youhua.infra.event.FailedEventRepository;
import com.youhua.profile.service.FinanceProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听 {@link ProfileRecalculationEvent}，在债务变更事务提交后异步重算用户财务画像。
 *
 * <p>使用 AFTER_COMMIT 确保数据已落库再读取，避免脏读。
 * <p>使用 @Async 避免阻塞业务主线程。
 * <p>任何异常均写入死信表 {@link FailedEventRepository}，由重试调度器后续处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileRecalculationListener {

    private final FinanceProfileService financeProfileService;
    private final FailedEventRepository failedEventRepository;

    @Async("asyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileRecalculation(ProfileRecalculationEvent event) {
        log.info("[ProfileRecalculationListener] Received event: userId={} debtId={} reason={}",
                event.getUserId(), event.getDebtId(), event.getTriggerReason());
        try {
            financeProfileService.recalculateForUser(event.getUserId());
            log.info("[ProfileRecalculationListener] Recalculation completed: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("[ProfileRecalculationListener] Recalculation failed: userId={} debtId={} reason={}",
                    event.getUserId(), event.getDebtId(), event.getTriggerReason(), e);
            failedEventRepository.save(
                    ProfileRecalculationEvent.class.getName(),
                    new FailedEventPayload(event.getUserId(), event.getDebtId(), event.getTriggerReason()),
                    e
            );
        }
    }

    /**
     * Serializable payload for storing in the dead-letter table.
     */
    public record FailedEventPayload(Long userId, Long debtId, String triggerReason) {}
}
