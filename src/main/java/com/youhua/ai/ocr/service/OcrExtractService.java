package com.youhua.ai.ocr.service;

import com.youhua.ai.enums.OcrFileType;
import com.youhua.ai.ocr.dto.OcrExtractResult;

/**
 * AI-powered OCR field extraction service.
 *
 * <p>Extracts structured debt fields from financial document images (contracts, bills, SMS screenshots)
 * by calling the DeepSeek large language model.
 *
 * <p>Financial calculations (APR, scoring) are never delegated to AI (F-02).
 * This service is a pure AI call wrapper with no database operations.
 */
public interface OcrExtractService {

    /**
     * Extract structured debt fields from a document image.
     *
     * @param imageBase64 Base64-encoded image content (never logged per F-04)
     * @param fileType    document type: CONTRACT / BILL / SMS_SCREENSHOT
     * @return extraction result; success=false with errorCode on OCR-level failures;
     *         throws BizException(AI_UNAVAILABLE) only when AI service is entirely down
     */
    OcrExtractResult extract(String imageBase64, OcrFileType fileType);
}
