package com.youhua.ai.ocr.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Structured debt fields extracted from a financial document via AI OCR.
 *
 * <p>All monetary and rate fields use BigDecimal (F-01).
 * Fields with confidence below 0.3 will have their value set to null.
 */
@Data
@Builder
public class OcrExtractedFields {

    /** Creditor institution name. */
    private OcrField<String> creditor;

    /** Loan principal amount (yuan). Must be BigDecimal. */
    private OcrField<BigDecimal> principal;

    /** Total repayment amount (yuan). May be derived from monthlyPayment * totalPeriods. */
    private OcrField<BigDecimal> totalRepayment;

    /** Nominal interest rate in decimal form (e.g. 0.045 for 4.5%). */
    private OcrField<BigDecimal> nominalRate;

    /** Loan duration in days. May be derived from startDate and endDate. */
    private OcrField<Integer> loanDays;

    /** Loan start date. */
    private OcrField<LocalDate> startDate;

    /** Loan end date. */
    private OcrField<LocalDate> endDate;

    /** Monthly payment amount (yuan). */
    private OcrField<BigDecimal> monthlyPayment;

    /** Total number of repayment periods. */
    private OcrField<Integer> totalPeriods;

    /** Total non-interest fees (service fees, handling fees, etc.) in yuan. */
    private OcrField<BigDecimal> fees;

    /** Penalty interest rate in decimal form. */
    private OcrField<BigDecimal> penaltyRate;
}
