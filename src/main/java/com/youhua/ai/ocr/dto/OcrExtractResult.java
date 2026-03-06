package com.youhua.ai.ocr.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Result of AI-powered OCR field extraction from a financial document.
 *
 * <p>On failure, {@code success=false} with errorCode and errorMessage set.
 * The service does NOT throw exceptions for OCR-level failures (non-JSON, empty result, etc.);
 * it only throws {@link com.youhua.common.exception.BizException} for AI service unavailability.
 */
@Data
@Builder
public class OcrExtractResult {

    /** Whether extraction succeeded with at least one meaningful field. */
    private boolean success;

    /**
     * Overall confidence score in 0-100 scale, scale=2.
     * Weighted: creditor*0.2 + principal*0.4 + loanDays*0.2 + totalRepayment*0.2, then *100.
     */
    private BigDecimal overallConfidence;

    /** True when overallConfidence < 60. */
    private boolean lowConfidence;

    /** Extracted fields; null when success=false. */
    private OcrExtractedFields fields;

    /**
     * Error code on failure: OCR_FAILED / OCR_PARSE_ERROR / OCR_TIMEOUT.
     * Null on success.
     */
    private String errorCode;

    /** Human-readable error message. Null on success. */
    private String errorMessage;
}
