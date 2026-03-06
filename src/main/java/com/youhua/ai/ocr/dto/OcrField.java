package com.youhua.ai.ocr.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Generic wrapper for a single OCR-extracted field with its confidence score.
 *
 * @param <T> the type of the extracted value
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrField<T> {

    /** Extracted value; null means the field was not recognized or had confidence < 0.3. */
    private T value;

    /** Confidence score in [0.0, 1.0], scale=2. */
    private BigDecimal confidence;
}
