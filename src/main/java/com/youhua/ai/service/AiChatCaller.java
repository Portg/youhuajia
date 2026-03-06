package com.youhua.ai.service;

import com.youhua.ai.exception.AiParseException;
import com.youhua.ai.exception.AiTimeoutException;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.infra.resilience.Resilient;
import com.youhua.infra.resilience.RetrySpec;
import com.youhua.infra.resilience.TimeoutSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Centralized ChatClient call wrapper with @Resilient annotations.
 *
 * <p>Extracted as a separate bean so that AOP proxying works correctly —
 * same-class method calls (this.method()) bypass Spring AOP.
 *
 * <p>F-04 compliance: no sensitive data (imageBase64, phone, ID card) is logged here.
 */
@Slf4j
@Component
public class AiChatCaller {

    private final ChatClient chatClient;

    public AiChatCaller(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Call ChatClient for OCR extraction — with retry on parse/timeout errors.
     */
    @Resilient(
            circuitBreaker = "deepseek",
            retry = @RetrySpec(maxAttempts = 1, retryOn = {AiParseException.class, AiTimeoutException.class}),
            timeout = @TimeoutSpec(seconds = 30)
    )
    public String callForOcr(String systemPrompt, String userPrompt) {
        return doCall(systemPrompt, userPrompt);
    }

    /**
     * Call ChatClient for suggestion generation — no retry (fallback handles failure).
     */
    @Resilient(
            circuitBreaker = "deepseek",
            timeout = @TimeoutSpec(seconds = 30)
    )
    public String callForSuggestion(String systemPrompt, String userPrompt) {
        return doCall(systemPrompt, userPrompt);
    }

    private String doCall(String systemPrompt, String userPrompt) {
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("read timeout")) {
                log.error("[AiChatCaller] AI call timed out", e);
                throw new AiTimeoutException("AI 调用超时", e);
            }
            log.error("[AiChatCaller] AI call failed", e);
            throw new BizException(ErrorCode.AI_UNAVAILABLE, "AI 服务不可用：" + e.getMessage(), e);
        }
    }
}
