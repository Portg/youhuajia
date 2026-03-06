package com.youhua.ai.ocr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.ai.enums.OcrFileType;
import com.youhua.ai.enums.OcrTaskStatus;
import com.youhua.ai.ocr.dto.OcrExtractResult;
import com.youhua.ai.ocr.dto.OcrExtractedFields;
import com.youhua.ai.ocr.dto.OcrField;
import com.youhua.ai.ocr.dto.request.ConfirmOcrTaskRequest;
import com.youhua.ai.ocr.dto.response.OcrTaskResponse;
import com.youhua.ai.ocr.entity.OcrTask;
import com.youhua.ai.ocr.mapper.OcrTaskMapper;
import com.youhua.ai.ocr.service.impl.OcrTaskServiceImpl;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.dto.response.DebtResponse;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.infra.log.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrTaskServiceImplTest {

    @Mock
    private OcrTaskMapper ocrTaskMapper;

    @Mock
    private OcrExtractService ocrExtractService;

    @Mock
    private DebtMapper debtMapper;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private OcrTaskServiceImpl ocrTaskService;

    private static final Long TEST_USER_ID = 100L;

    @BeforeEach
    void setUp() {
        // Inject ObjectMapper via reflection since @InjectMocks won't inject it directly
        org.springframework.test.util.ReflectionTestUtils.setField(
                ocrTaskService, "objectMapper", new ObjectMapper().findAndRegisterModules());

        // Set up request context for userId extraction
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setAttribute("userId", TEST_USER_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void should_throw_OCR_FILE_FORMAT_INVALID_when_file_format_unsupported() {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes());

        // When & Then
        assertThatThrownBy(() -> ocrTaskService.createOcrTask(file, OcrFileType.BILL))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.OCR_FILE_FORMAT_INVALID));

        verify(ocrTaskMapper, never()).insert((OcrTask) any(OcrTask.class));
    }

    @Test
    void should_throw_OCR_FILE_SIZE_EXCEEDED_when_file_too_large() {
        // Given: file > 10MB
        byte[] largeContent = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.jpg", "image/jpeg", largeContent);

        // When & Then
        assertThatThrownBy(() -> ocrTaskService.createOcrTask(file, OcrFileType.CONTRACT))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.OCR_FILE_SIZE_EXCEEDED));
    }

    @Test
    void should_create_ocr_task_and_return_response_when_ocr_succeeds() {
        // Given
        byte[] content = "fake image content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.jpg", "image/jpeg", content);

        doAnswer(inv -> {
            OcrTask t = inv.getArgument(0);
            t.setId(100L);
            return 1;
        }).when(ocrTaskMapper).insert((OcrTask) any(OcrTask.class));

        OcrExtractResult successResult = OcrExtractResult.builder()
                .success(true)
                .overallConfidence(new BigDecimal("85.50"))
                .lowConfidence(false)
                .fields(OcrExtractedFields.builder()
                        .creditor(new OcrField<>("招商银行", new BigDecimal("0.95")))
                        .principal(new OcrField<>(new BigDecimal("50000"), new BigDecimal("0.90")))
                        .build())
                .build();
        when(ocrExtractService.extract(any(), eq(OcrFileType.CONTRACT))).thenReturn(successResult);

        OcrTask savedTask = new OcrTask();
        savedTask.setId(100L);
        savedTask.setUserId(TEST_USER_ID);
        savedTask.setFileType(OcrFileType.CONTRACT);
        savedTask.setStatus(OcrTaskStatus.SUCCESS);
        savedTask.setConfidenceScore(new BigDecimal("85.50"));
        when(ocrTaskMapper.selectById(100L)).thenReturn(savedTask);

        // When
        OcrTaskResponse response = ocrTaskService.createOcrTask(file, OcrFileType.CONTRACT);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("ocr-tasks/100");
        assertThat(response.getStatus()).isEqualTo(OcrTaskStatus.SUCCESS);
        verify(ocrExtractService).extract(any(), eq(OcrFileType.CONTRACT));
    }

    @Test
    void should_throw_OCR_TASK_NOT_FOUND_when_task_does_not_exist() {
        // Given
        when(ocrTaskMapper.selectById(999L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> ocrTaskService.getOcrTask(999L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.OCR_TASK_NOT_FOUND));
    }

    @Test
    void should_throw_OCR_TASK_PROCESSING_when_confirming_non_success_task() {
        // Given
        OcrTask task = new OcrTask();
        task.setId(200L);
        task.setUserId(TEST_USER_ID);
        task.setStatus(OcrTaskStatus.FAILED);

        when(ocrTaskMapper.selectById(200L)).thenReturn(task);

        ConfirmOcrTaskRequest request = new ConfirmOcrTaskRequest();

        // When & Then
        assertThatThrownBy(() -> ocrTaskService.confirmOcrTask(200L, request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.OCR_TASK_PROCESSING));
    }

    @Test
    void should_confirm_ocr_task_and_create_debt_when_task_is_success() {
        // Given
        OcrTask task = new OcrTask();
        task.setId(300L);
        task.setUserId(TEST_USER_ID);
        task.setStatus(OcrTaskStatus.SUCCESS);
        task.setConfidenceScore(new BigDecimal("80.00"));
        task.setExtractedFieldsJson("{\"creditor\":{\"value\":\"工商银行\",\"confidence\":0.92}," +
                "\"principal\":{\"value\":100000,\"confidence\":0.95}}");

        when(ocrTaskMapper.selectById(300L)).thenReturn(task);
        doAnswer(inv -> {
            Debt d = inv.getArgument(0);
            d.setId(400L);
            return 1;
        }).when(debtMapper).insert((Debt) any(Debt.class));

        ConfirmOcrTaskRequest request = new ConfirmOcrTaskRequest();
        request.setCorrections(Map.of("creditor", "工商银行（修正）"));

        // When
        DebtResponse response = ocrTaskService.confirmOcrTask(300L, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("debts/400");
        verify(debtMapper).insert((Debt) any(Debt.class));
        verify(ocrTaskMapper).updateById((OcrTask) any(OcrTask.class));
    }

    @Test
    void should_throw_OCR_RETRY_EXCEEDED_when_retry_count_reaches_limit() {
        // Given
        OcrTask task = new OcrTask();
        task.setId(500L);
        task.setUserId(TEST_USER_ID);
        task.setStatus(OcrTaskStatus.FAILED);
        task.setRetryCount(3);
        task.setFileUrl("/tmp/ocr/500");

        when(ocrTaskMapper.selectById(500L)).thenReturn(task);

        // When & Then
        assertThatThrownBy(() -> ocrTaskService.retryOcrTask(500L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.OCR_RETRY_EXCEEDED));

        verify(ocrExtractService, never()).extract(any(), any());
    }
}
