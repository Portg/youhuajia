package com.youhua.ai.exception;

/**
 * OCR produced an empty or meaningless result.
 * This is NOT retryable — the same image will produce the same empty result.
 */
public class OcrEmptyResultException extends RuntimeException {

    public OcrEmptyResultException(String message) {
        super(message);
    }
}
