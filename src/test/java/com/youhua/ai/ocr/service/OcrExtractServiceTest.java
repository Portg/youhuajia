package com.youhua.ai.ocr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.ai.enums.OcrFileType;
import com.youhua.ai.exception.AiParseException;
import com.youhua.ai.exception.AiTimeoutException;
import com.youhua.ai.ocr.dto.OcrExtractResult;
import com.youhua.ai.ocr.service.impl.OcrExtractServiceImpl;
import com.youhua.ai.service.AiChatCaller;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OcrExtractServiceImpl.
 *
 * <p>AiChatCaller is mocked — no actual AI calls are made.
 * Covers the 5 spec-defined test cases plus additional boundary/error scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OcrExtractService Tests")
class OcrExtractServiceTest {

    @Mock
    private AiChatCaller aiChatCaller;

    @Mock
    private CloudOcrService cloudOcrService;

    private OcrExtractServiceImpl ocrExtractService;

    @BeforeEach
    void setUp() {
        // Default: cloudOcrService returns dummy text (actual OCR text is irrelevant since AI response is mocked)
        lenient().when(cloudOcrService.extractText(anyString())).thenReturn("OCR extracted text");
        ocrExtractService = new OcrExtractServiceImpl(aiChatCaller, new ObjectMapper(), cloudOcrService);
    }

    // ===== Helper: mock a single AI response =====

    private void mockAiResponse(String content) {
        when(aiChatCaller.callForOcr(anyString(), anyString())).thenReturn(content);
    }

    /**
     * Mock AI to throw AiParseException (simulates retry exhausted — the @Resilient
     * on AiChatCaller handles the actual retry; if it still fails, AiParseException propagates).
     */
    private void mockAiParseException() {
        when(aiChatCaller.callForOcr(anyString(), anyString()))
                .thenThrow(new AiParseException("AI 返回内容无法解析"));
    }

    private void mockAiTimeoutException() {
        when(aiChatCaller.callForOcr(anyString(), anyString()))
                .thenThrow(new AiTimeoutException("AI 调用超时"));
    }

    private void mockAiUnavailable() {
        when(aiChatCaller.callForOcr(anyString(), anyString()))
                .thenThrow(new BizException(ErrorCode.AI_UNAVAILABLE));
    }

    // ===== Spec Test Case 1: Standard contract (high confidence) =====

    @Test
    @DisplayName("OCR-N01: should_return_high_confidence_result_when_standard_contract_json")
    void should_return_high_confidence_result_when_standard_contract_json() {
        String json = """
                {
                  "creditor": { "value": "招商银行", "confidence": 0.95 },
                  "principal": { "value": 50000.00, "confidence": 0.92 },
                  "totalRepayment": { "value": 62400.00, "confidence": 0.88 },
                  "nominalRate": { "value": 0.086, "confidence": 0.90 },
                  "loanDays": { "value": 365, "confidence": 0.85 },
                  "startDate": { "value": "2025-01-15", "confidence": 0.90 },
                  "endDate": { "value": "2026-01-15", "confidence": 0.90 },
                  "monthlyPayment": { "value": 5200.00, "confidence": 0.88 },
                  "totalPeriods": { "value": 12, "confidence": 0.92 },
                  "fees": { "value": 500.00, "confidence": 0.75 },
                  "penaltyRate": { "value": 0.0005, "confidence": 0.60 }
                }
                """;

        mockAiResponse(json);

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.CONTRACT);

        // success=true, overallConfidence=90.40, lowConfidence=false
        // calculation: (0.95*0.2 + 0.92*0.4 + 0.85*0.2 + 0.88*0.2)*100 = 90.40
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isLowConfidence()).isFalse();
        assertThat(result.getOverallConfidence()).isEqualByComparingTo(new BigDecimal("90.40"));
        assertThat(result.getErrorCode()).isNull();

        // All fields mapped correctly
        assertThat(result.getFields().getCreditor().getValue()).isEqualTo("招商银行");
        assertThat(result.getFields().getPrincipal().getValue()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(result.getFields().getTotalRepayment().getValue()).isEqualByComparingTo(new BigDecimal("62400.00"));
        assertThat(result.getFields().getNominalRate().getValue()).isEqualByComparingTo(new BigDecimal("0.086"));
        assertThat(result.getFields().getLoanDays().getValue()).isEqualTo(365);
        assertThat(result.getFields().getStartDate().getValue()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(result.getFields().getEndDate().getValue()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(result.getFields().getMonthlyPayment().getValue()).isEqualByComparingTo(new BigDecimal("5200.00"));
        assertThat(result.getFields().getTotalPeriods().getValue()).isEqualTo(12);
        assertThat(result.getFields().getFees().getValue()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    // ===== Spec Test Case 2: SMS screenshot (low confidence, partial fields) =====

    @Test
    @DisplayName("OCR-N02: should_return_low_confidence_and_derive_total_repayment_when_sms_screenshot")
    void should_return_low_confidence_and_derive_total_repayment_when_sms_screenshot() {
        String json = """
                {
                  "creditor": { "value": "某消费金融", "confidence": 0.60 },
                  "principal": { "value": 20000.00, "confidence": 0.55 },
                  "totalRepayment": { "value": null, "confidence": 0 },
                  "nominalRate": { "value": null, "confidence": 0 },
                  "loanDays": { "value": null, "confidence": 0 },
                  "startDate": { "value": null, "confidence": 0 },
                  "endDate": { "value": null, "confidence": 0 },
                  "monthlyPayment": { "value": 2100.00, "confidence": 0.50 },
                  "totalPeriods": { "value": 12, "confidence": 0.45 },
                  "fees": { "value": null, "confidence": 0 },
                  "penaltyRate": { "value": null, "confidence": 0 }
                }
                """;

        mockAiResponse(json);

        OcrExtractResult result = ocrExtractService.extract("base64sms", OcrFileType.SMS_SCREENSHOT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isLowConfidence()).isTrue();
        assertThat(result.getOverallConfidence()).isEqualByComparingTo(new BigDecimal("34.00"));

        // totalRepayment derived: 2100 * 12 = 25200
        assertThat(result.getFields().getTotalRepayment().getValue()).isEqualByComparingTo(new BigDecimal("25200.0"));
        assertThat(result.getFields().getTotalRepayment().getConfidence()).isEqualByComparingTo(new BigDecimal("0.45"));
    }

    // ===== Spec Test Case 3: Parse failure after retries → OCR_PARSE_ERROR =====

    @Test
    @DisplayName("OCR-N03: should_return_parse_error_when_ai_parse_exception_propagates")
    void should_return_parse_error_when_ai_parse_exception_propagates() {
        // AiChatCaller.callForOcr handles retry internally via @Resilient;
        // if it still fails, AiParseException propagates to OcrExtractServiceImpl
        mockAiParseException();

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.CONTRACT);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("OCR_PARSE_ERROR");
    }

    // ===== Spec Test Case 4: Low-confidence fields filtered, derived totalRepayment =====

    @Test
    @DisplayName("OCR-N04: should_filter_low_confidence_fields_and_derive_total_repayment")
    void should_filter_low_confidence_fields_and_derive_total_repayment() {
        String json = """
                {
                  "creditor": { "value": "某某公司", "confidence": 0.25 },
                  "principal": { "value": 10000.00, "confidence": 0.88 },
                  "totalRepayment": { "value": 12000.00, "confidence": 0.20 },
                  "nominalRate": { "value": 0.10, "confidence": 0.15 },
                  "loanDays": { "value": 180, "confidence": 0.70 },
                  "startDate": { "value": "2025-06-01", "confidence": 0.10 },
                  "endDate": { "value": "2025-12-01", "confidence": 0.10 },
                  "monthlyPayment": { "value": 2000.00, "confidence": 0.60 },
                  "totalPeriods": { "value": 6, "confidence": 0.70 },
                  "fees": { "value": 200.00, "confidence": 0.05 },
                  "penaltyRate": { "value": null, "confidence": 0 }
                }
                """;

        mockAiResponse(json);

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.BILL);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFields().getCreditor().getValue()).isNull();
        assertThat(result.getFields().getTotalRepayment().getValue()).isEqualByComparingTo(new BigDecimal("12000"));
        assertThat(result.getFields().getTotalRepayment().getConfidence()).isEqualByComparingTo(new BigDecimal("0.60"));
        assertThat(result.getFields().getNominalRate().getValue()).isNull();
        assertThat(result.getFields().getStartDate().getValue()).isNull();
        assertThat(result.getFields().getEndDate().getValue()).isNull();
        assertThat(result.getFields().getFees().getValue()).isNull();
        assertThat(result.getFields().getLoanDays().getValue()).isEqualTo(180);
        assertThat(result.getOverallConfidence()).isEqualByComparingTo(new BigDecimal("61.20"));
        assertThat(result.isLowConfidence()).isFalse();
    }

    // ===== Spec Test Case 5: All core fields null → OCR_FAILED =====

    @Test
    @DisplayName("OCR-N05: should_return_ocr_failed_when_all_fields_are_null")
    void should_return_ocr_failed_when_all_fields_are_null() {
        String json = """
                {
                  "creditor": { "value": null, "confidence": 0 },
                  "principal": { "value": null, "confidence": 0 },
                  "totalRepayment": { "value": null, "confidence": 0 },
                  "nominalRate": { "value": null, "confidence": 0 },
                  "loanDays": { "value": null, "confidence": 0 },
                  "startDate": { "value": null, "confidence": 0 },
                  "endDate": { "value": null, "confidence": 0 },
                  "monthlyPayment": { "value": null, "confidence": 0 },
                  "totalPeriods": { "value": null, "confidence": 0 },
                  "fees": { "value": null, "confidence": 0 },
                  "penaltyRate": { "value": null, "confidence": 0 }
                }
                """;

        mockAiResponse(json);

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.BILL);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("OCR_FAILED");
        assertThat(result.getErrorMessage()).contains("手动录入");
        assertThat(result.getFields()).isNull();
    }

    // ===== Additional: empty AI response =====

    @Test
    @DisplayName("OCR-E02: should_return_ocr_failed_when_ai_returns_empty_string")
    void should_return_ocr_failed_when_ai_returns_empty_string() {
        mockAiResponse("");

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.SMS_SCREENSHOT);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("OCR_FAILED");
    }

    // ===== Additional: AI service unavailable throws BizException =====

    @Test
    @DisplayName("OCR-E03: should_throw_biz_exception_when_ai_service_unavailable")
    void should_throw_biz_exception_when_ai_service_unavailable() {
        mockAiUnavailable();

        assertThatThrownBy(() -> ocrExtractService.extract("base64content", OcrFileType.CONTRACT))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
    }

    // ===== Additional: timeout returns OCR_TIMEOUT =====

    @Test
    @DisplayName("OCR-E04: should_return_ocr_timeout_when_ai_call_times_out_after_retries")
    void should_return_ocr_timeout_when_ai_call_times_out_after_retries() {
        mockAiTimeoutException();

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.CONTRACT);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("OCR_TIMEOUT");
    }

    // ===== Additional: principal <= 0 is nulled =====

    @Test
    @DisplayName("OCR-B01: should_null_principal_when_value_is_zero_or_negative")
    void should_null_principal_when_value_is_zero_or_negative() {
        String json = """
                {
                  "creditor": { "value": "某机构", "confidence": 0.90 },
                  "principal": { "value": 0, "confidence": 0.90 },
                  "totalRepayment": { "value": 1000, "confidence": 0.90 },
                  "nominalRate": { "value": null, "confidence": 0 },
                  "loanDays": { "value": 30, "confidence": 0.80 },
                  "startDate": { "value": null, "confidence": 0 },
                  "endDate": { "value": null, "confidence": 0 },
                  "monthlyPayment": { "value": null, "confidence": 0 },
                  "totalPeriods": { "value": null, "confidence": 0 },
                  "fees": { "value": null, "confidence": 0 },
                  "penaltyRate": { "value": null, "confidence": 0 }
                }
                """;

        mockAiResponse(json);

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.CONTRACT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFields().getPrincipal().getValue()).isNull();
        assertThat(result.getFields().getPrincipal().getConfidence()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ===== Additional: derive loanDays from dates =====

    @Test
    @DisplayName("OCR-B02: should_derive_loan_days_when_start_and_end_date_present")
    void should_derive_loan_days_when_start_and_end_date_present() {
        String json = """
                {
                  "creditor": { "value": "平安银行", "confidence": 0.90 },
                  "principal": { "value": 20000, "confidence": 0.90 },
                  "totalRepayment": { "value": 22000, "confidence": 0.85 },
                  "nominalRate": { "value": null, "confidence": 0 },
                  "loanDays": { "value": null, "confidence": 0 },
                  "startDate": { "value": "2024-01-01", "confidence": 0.85 },
                  "endDate": { "value": "2024-07-01", "confidence": 0.85 },
                  "monthlyPayment": { "value": null, "confidence": 0 },
                  "totalPeriods": { "value": null, "confidence": 0 },
                  "fees": { "value": null, "confidence": 0 },
                  "penaltyRate": { "value": null, "confidence": 0 }
                }
                """;

        mockAiResponse(json);

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.CONTRACT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFields().getLoanDays().getValue()).isEqualTo(182);
    }

    // ===== Additional: all monetary fields use BigDecimal =====

    @Test
    @DisplayName("OCR-B03: should_use_bigdecimal_for_all_monetary_fields")
    void should_use_bigdecimal_for_all_monetary_fields() {
        String json = """
                {
                  "creditor": { "value": "测试银行", "confidence": 0.90 },
                  "principal": { "value": 100000.5678, "confidence": 0.90 },
                  "totalRepayment": { "value": 110000.1234, "confidence": 0.90 },
                  "nominalRate": { "value": 0.0456789, "confidence": 0.85 },
                  "loanDays": { "value": 365, "confidence": 0.85 },
                  "startDate": { "value": null, "confidence": 0 },
                  "endDate": { "value": null, "confidence": 0 },
                  "monthlyPayment": { "value": null, "confidence": 0 },
                  "totalPeriods": { "value": null, "confidence": 0 },
                  "fees": { "value": null, "confidence": 0 },
                  "penaltyRate": { "value": null, "confidence": 0 }
                }
                """;

        mockAiResponse(json);

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.CONTRACT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFields().getPrincipal().getValue()).isInstanceOf(BigDecimal.class);
        assertThat(result.getFields().getTotalRepayment().getValue()).isInstanceOf(BigDecimal.class);
        assertThat(result.getFields().getNominalRate().getValue()).isInstanceOf(BigDecimal.class);
    }

    // ===== Additional: markdown-wrapped JSON parsed correctly =====

    @Test
    @DisplayName("OCR-B04: should_parse_json_when_wrapped_in_markdown_code_block")
    void should_parse_json_when_wrapped_in_markdown_code_block() {
        String wrapped = """
                ```json
                {
                  "creditor": { "value": "建设银行", "confidence": 0.90 },
                  "principal": { "value": 15000, "confidence": 0.90 },
                  "totalRepayment": { "value": 16500, "confidence": 0.85 },
                  "nominalRate": { "value": null, "confidence": 0 },
                  "loanDays": { "value": 180, "confidence": 0.80 },
                  "startDate": { "value": null, "confidence": 0 },
                  "endDate": { "value": null, "confidence": 0 },
                  "monthlyPayment": { "value": null, "confidence": 0 },
                  "totalPeriods": { "value": null, "confidence": 0 },
                  "fees": { "value": null, "confidence": 0 },
                  "penaltyRate": { "value": null, "confidence": 0 }
                }
                ```""";

        mockAiResponse(wrapped);

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.CONTRACT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFields().getCreditor().getValue()).isEqualTo("建设银行");
    }

    // ===== Additional: valid JSON with extra unknown fields is silently ignored =====

    @Test
    @DisplayName("OCR-U01: should_parse_correctly_and_ignore_extra_fields_when_ai_returns_extra_properties")
    void should_parse_correctly_and_ignore_extra_fields_when_ai_returns_extra_properties() {
        String json = """
                {
                  "creditor": { "value": "光大银行", "confidence": 0.92 },
                  "principal": { "value": 80000, "confidence": 0.95 },
                  "totalRepayment": { "value": 90000, "confidence": 0.90 },
                  "nominalRate": { "value": 0.0375, "confidence": 0.85 },
                  "loanDays": { "value": 730, "confidence": 0.90 },
                  "startDate": { "value": null, "confidence": 0 },
                  "endDate": { "value": null, "confidence": 0 },
                  "monthlyPayment": { "value": 3750, "confidence": 0.88 },
                  "totalPeriods": { "value": 24, "confidence": 0.90 },
                  "fees": { "value": 800, "confidence": 0.75 },
                  "penaltyRate": { "value": null, "confidence": 0 },
                  "unknownExtraField": { "value": "should be ignored", "confidence": 1.0 }
                }
                """;

        mockAiResponse(json);

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.CONTRACT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFields().getCreditor().getValue()).isEqualTo("光大银行");
        assertThat(result.getFields().getPrincipal().getValue()).isEqualByComparingTo(new BigDecimal("80000"));
    }

    // ===== Additional: null AI response → OCR_FAILED =====

    @Test
    @DisplayName("OCR-U02: should_return_ocr_failed_when_ai_returns_null")
    void should_return_ocr_failed_when_ai_returns_null() {
        mockAiResponse(null);

        OcrExtractResult result = ocrExtractService.extract("base64content", OcrFileType.CONTRACT);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("OCR_FAILED");
    }
}
