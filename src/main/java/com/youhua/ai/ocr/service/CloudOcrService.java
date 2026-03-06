package com.youhua.ai.ocr.service;

/**
 * Cloud OCR service for extracting raw text from images.
 *
 * <p>Abstracts the underlying cloud OCR provider (Baidu, Tencent, etc.).
 * The extracted text is then passed to DeepSeek for structured field extraction.
 */
public interface CloudOcrService {

    /**
     * Extract raw text from an image.
     *
     * @param imageBase64 Base64-encoded image content (never logged per F-04)
     * @return extracted text from the image
     * @throws com.youhua.common.exception.BizException on OCR API failure
     */
    String extractText(String imageBase64);
}
