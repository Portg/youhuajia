package com.youhua.infra.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.youhua.infra.mapper.FailedEventMapper;

import java.time.LocalDateTime;

/**
 * 死信事件存储仓库——供事件监听器在 catch 块中调用，持久化失败事件。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FailedEventRepository {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final FailedEventMapper failedEventMapper;
    private final ObjectMapper objectMapper;

    /**
     * 持久化失败事件到死信表。
     *
     * @param eventType  事件类型全限定类名
     * @param payload    事件对象（将被序列化为 JSON）
     * @param error      处理时抛出的异常
     */
    public void save(String eventType, Object payload, Throwable error) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String errorMessage = error.getMessage();
            if (errorMessage != null && errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
                errorMessage = errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
            }

            FailedEvent failedEvent = new FailedEvent();
            failedEvent.setEventType(eventType);
            failedEvent.setPayload(payloadJson);
            failedEvent.setErrorMessage(errorMessage);
            failedEvent.setRetryCount(0);
            failedEvent.setNextRetryTime(LocalDateTime.now().plusMinutes(5));
            failedEvent.setCreateTime(LocalDateTime.now());
            failedEvent.setDeleted(0);

            failedEventMapper.insert(failedEvent);
            log.info("[FailedEvent] Saved failed event: type={} id={}", eventType, failedEvent.getId());
        } catch (JsonProcessingException e) {
            log.error("[FailedEvent] Failed to serialize payload for eventType={}", eventType, e);
        } catch (Exception e) {
            log.error("[FailedEvent] Failed to persist failed event: type={}", eventType, e);
        }
    }
}
