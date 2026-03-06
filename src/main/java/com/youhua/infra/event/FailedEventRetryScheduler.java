package com.youhua.infra.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.infra.mapper.FailedEventMapper;
import com.youhua.debt.event.ProfileRecalculationEvent;
import com.youhua.profile.event.ProfileRecalculationListener;
import com.youhua.profile.service.FinanceProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 死信事件重试调度器。
 *
 * <p>每 5 分钟扫描一次死信表，对满足条件（retry_count < 3, next_retry_time <= now）的事件进行重试。
 * <ul>
 *   <li>重试成功：逻辑删除该记录</li>
 *   <li>重试失败：retry_count++，next_retry_time = now + 5min * 2^retryCount（指数退避）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FailedEventRetryScheduler {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long BASE_RETRY_MINUTES = 5L;

    private final FailedEventMapper failedEventMapper;
    private final ObjectMapper objectMapper;
    private final FinanceProfileService financeProfileService;

    @Scheduled(fixedDelay = 300_000)
    public void retryFailedEvents() {
        List<FailedEvent> candidates = failedEventMapper.selectList(
                new LambdaQueryWrapper<FailedEvent>()
                        .eq(FailedEvent::getDeleted, 0)
                        .lt(FailedEvent::getRetryCount, MAX_RETRY_COUNT)
                        .le(FailedEvent::getNextRetryTime, LocalDateTime.now())
        );

        if (candidates.isEmpty()) {
            return;
        }

        log.info("[FailedEventRetry] Found {} events to retry", candidates.size());

        for (FailedEvent event : candidates) {
            retryOne(event);
        }
    }

    private void retryOne(FailedEvent event) {
        log.info("[FailedEventRetry] Retrying event: id={} type={} retryCount={}",
                event.getId(), event.getEventType(), event.getRetryCount());
        try {
            dispatch(event);

            // success — logical delete
            event.setDeleted(1);
            failedEventMapper.updateById(event);
            log.info("[FailedEventRetry] Retry succeeded: id={}", event.getId());

        } catch (Exception e) {
            int newRetryCount = event.getRetryCount() + 1;
            long delayMinutes = BASE_RETRY_MINUTES * (1L << event.getRetryCount()); // 5 * 2^n
            LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(delayMinutes);

            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500);
            }

            event.setRetryCount(newRetryCount);
            event.setNextRetryTime(nextRetry);
            event.setErrorMessage(errorMessage);
            failedEventMapper.updateById(event);

            log.warn("[FailedEventRetry] Retry failed: id={} newRetryCount={} nextRetry={}",
                    event.getId(), newRetryCount, nextRetry, e);
        }
    }

    /**
     * Dispatches the stored event payload to the appropriate handler.
     * Currently only ProfileRecalculationEvent is supported.
     */
    private void dispatch(FailedEvent event) throws Exception {
        String eventType = event.getEventType();

        if (ProfileRecalculationEvent.class.getName().equals(eventType)) {
            ProfileRecalculationListener.FailedEventPayload payload =
                    objectMapper.readValue(event.getPayload(), ProfileRecalculationListener.FailedEventPayload.class);
            financeProfileService.recalculateForUser(payload.userId());
        } else {
            log.warn("[FailedEventRetry] Unknown event type, skipping: id={} type={}", event.getId(), eventType);
            // Mark as exhausted to prevent infinite skipping
            throw new IllegalArgumentException("Unknown event type: " + eventType);
        }
    }
}
