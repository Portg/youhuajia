package com.youhua.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.ai.enums.OcrFileType;
import com.youhua.ai.enums.OcrTaskStatus;
import com.youhua.ai.ocr.controller.OcrTaskController;
import com.youhua.ai.ocr.dto.response.OcrTaskResponse;
import com.youhua.ai.ocr.service.OcrTaskService;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.dto.response.DebtResponse;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.enums.DebtType;
import com.youhua.debt.enums.OverdueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for OCR task API.
 * Covers API-OCR01 ~ API-OCR07 from test-matrix.md.
 */
@WebMvcTest(controllers = OcrTaskController.class)
@Import(com.youhua.common.config.GlobalExceptionHandler.class)
@DisplayName("OcrTaskController Integration Tests — API-OCR01~07")
class OcrTaskIntegrationTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OcrTaskService ocrTaskService;

    private OcrTaskResponse buildTaskResponse(OcrTaskStatus status) {
        return OcrTaskResponse.builder()
                .name("ocr-tasks/200001")
                .fileType(OcrFileType.CONTRACT)
                .status(status)
                .confidenceScore(new BigDecimal("85.50"))
                .extractedFields(Map.of(
                        "creditor", Map.of("value", "招商银行", "confidence", 0.95),
                        "principal", Map.of("value", 100000.00, "confidence", 0.88)
                ))
                .build();
    }

    // ---- API-OCR01: 正常上传 JPG + CONTRACT ----

    @Test
    @DisplayName("should_return_200_with_task_id_when_jpg_contract_file_uploaded")
    void should_return_200_with_task_id_when_jpg_contract_file_uploaded() throws Exception {
        when(ocrTaskService.createOcrTask(any(), eq(OcrFileType.CONTRACT)))
                .thenReturn(buildTaskResponse(OcrTaskStatus.PENDING));

        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-content".getBytes());

        mockMvc.perform(multipart("/api/v1/ocr-tasks")
                        .file(file)
                        .param("fileType", "CONTRACT")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ocr-tasks/200001"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ---- API-OCR02: 不支持格式 (.doc) ----

    @Test
    @DisplayName("should_return_405001_when_unsupported_file_format_uploaded")
    void should_return_405001_when_unsupported_file_format_uploaded() throws Exception {
        when(ocrTaskService.createOcrTask(any(), any()))
                .thenThrow(new BizException(ErrorCode.OCR_FILE_FORMAT_INVALID));

        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.doc", MediaType.APPLICATION_OCTET_STREAM_VALUE, "doc-content".getBytes());

        mockMvc.perform(multipart("/api/v1/ocr-tasks")
                        .file(file)
                        .param("fileType", "CONTRACT")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.OCR_FILE_FORMAT_INVALID.getCode()))
                .andExpect(jsonPath("$.error.status").value("INVALID_ARGUMENT"));
    }

    // ---- API-OCR03: 超大文件 (15MB > 10MB limit) ----

    @Test
    @DisplayName("should_return_405002_when_file_exceeds_10mb_size_limit")
    void should_return_405002_when_file_exceeds_10mb_size_limit() throws Exception {
        when(ocrTaskService.createOcrTask(any(), any()))
                .thenThrow(new BizException(ErrorCode.OCR_FILE_SIZE_EXCEEDED));

        byte[] oversizedContent = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.jpg", MediaType.IMAGE_JPEG_VALUE, oversizedContent);

        mockMvc.perform(multipart("/api/v1/ocr-tasks")
                        .file(file)
                        .param("fileType", "CONTRACT")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.OCR_FILE_SIZE_EXCEEDED.getCode()));
    }

    // ---- API-OCR04: 查询结果 - 处理中 ----

    @Test
    @DisplayName("should_return_200_with_PROCESSING_status_when_task_still_running")
    void should_return_200_with_PROCESSING_status_when_task_still_running() throws Exception {
        when(ocrTaskService.getOcrTask(200001L))
                .thenReturn(buildTaskResponse(OcrTaskStatus.PROCESSING));

        mockMvc.perform(get("/api/v1/ocr-tasks/200001")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    // ---- API-OCR05: 查询结果 - 成功，含extractedFields ----

    @Test
    @DisplayName("should_return_200_with_extractedFields_when_task_completed_successfully")
    void should_return_200_with_extractedFields_when_task_completed_successfully() throws Exception {
        when(ocrTaskService.getOcrTask(200001L))
                .thenReturn(buildTaskResponse(OcrTaskStatus.SUCCESS));

        mockMvc.perform(get("/api/v1/ocr-tasks/200001")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.extractedFields").isMap())
                .andExpect(jsonPath("$.extractedFields.creditor").isMap());
    }

    // ---- API-OCR06: 确认OCR，生成Debt记录 ----

    @Test
    @DisplayName("should_return_200_with_debt_resource_when_ocr_result_confirmed")
    void should_return_200_with_debt_resource_when_ocr_result_confirmed() throws Exception {
        DebtResponse debtResponse = DebtResponse.builder()
                .name("debts/3001")
                .creditor("招商银行")
                .debtType(DebtType.CREDIT_CARD)
                .principal(new BigDecimal("100000.0000"))
                .totalRepayment(new BigDecimal("118000.0000"))
                .loanDays(365)
                .sourceType(DebtSourceType.OCR)
                .confidenceScore(new BigDecimal("85.50"))
                .status(DebtStatus.CONFIRMED)
                .overdueStatus(OverdueStatus.NORMAL)
                .version(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        when(ocrTaskService.confirmOcrTask(anyLong(), any())).thenReturn(debtResponse);

        String confirmBody = objectMapper.writeValueAsString(
                new com.youhua.ai.ocr.dto.request.ConfirmOcrTaskRequest());

        mockMvc.perform(post("/api/v1/ocr-tasks/200001:confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("debts/3001"))
                .andExpect(jsonPath("$.sourceType").value("OCR"));
    }

    // ---- API-OCR07: 重试超限 (retryCount=3) ----

    @Test
    @DisplayName("should_return_405006_when_ocr_retry_count_reaches_limit")
    void should_return_405006_when_ocr_retry_count_reaches_limit() throws Exception {
        when(ocrTaskService.retryOcrTask(200003L))
                .thenThrow(new BizException(ErrorCode.OCR_RETRY_EXCEEDED));

        mockMvc.perform(post("/api/v1/ocr-tasks/200003:retry")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.OCR_RETRY_EXCEEDED.getCode()))
                .andExpect(jsonPath("$.error.status").value("RESOURCE_EXHAUSTED"));
    }
}
