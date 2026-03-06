package com.youhua.ai.exception;

/**
 * AI service call timed out.
 * This is a retryable exception — a subsequent attempt may succeed.
 */
public class AiTimeoutException extends RuntimeException {

    public AiTimeoutException(String message) {
        super(message);
    }

    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
