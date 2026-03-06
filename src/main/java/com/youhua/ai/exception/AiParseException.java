package com.youhua.ai.exception;

/**
 * AI response could not be parsed into the expected structure.
 * This is a retryable exception — the next attempt may produce valid output.
 */
public class AiParseException extends RuntimeException {

    public AiParseException(String message) {
        super(message);
    }

    public AiParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
