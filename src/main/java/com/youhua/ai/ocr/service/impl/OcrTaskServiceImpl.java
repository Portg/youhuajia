package com.youhua.ai.ocr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.ai.enums.OcrFileType;
import com.youhua.ai.enums.OcrTaskStatus;
import com.youhua.ai.ocr.dto.OcrExtractResult;
import com.youhua.ai.ocr.dto.OcrExtractedFields;
import com.youhua.ai.ocr.dto.request.ConfirmOcrTaskRequest;
import com.youhua.ai.ocr.dto.response.OcrTaskResponse;
import com.youhua.ai.ocr.entity.OcrTask;
import com.youhua.ai.ocr.mapper.OcrTaskMapper;
import com.youhua.ai.ocr.service.OcrExtractService;
import com.youhua.ai.ocr.service.OcrTaskService;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.common.util.RequestContextUtil;
import com.youhua.debt.dto.response.DebtResponse;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.infra.log.OperationLogService;
import com.youhua.infra.log.enums.OperationAction;
import com.youhua.infra.log.enums.OperationModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * OCR task service implementation.
 *
 * <p>F-04: No sensitive user data (phone, ID number) in logs.
 * F-01: All monetary amounts use BigDecimal.
 * F-05: No @Transactional nesting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrTaskServiceImpl implements OcrTaskService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB
    private static final int MAX_RETRY_COUNT = 3;

    private final OcrTaskMapper ocrTaskMapper;
    private final OcrExtractService ocrExtractService;
    private final DebtMapper debtMapper;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Override
    public OcrTaskResponse createOcrTask(MultipartFile file, OcrFileType fileType) {
        // File format validation
        validateFileFormat(file);

        // File size validation
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            log.warn("[OcrTaskService] File size exceeded: {}bytes", file.getSize());
            throw new BizException(ErrorCode.OCR_FILE_SIZE_EXCEEDED);
        }

        // Create OcrTask record
        Long userId = RequestContextUtil.getCurrentUserId();
        String fileUrl = "/tmp/ocr/" + java.util.UUID.randomUUID().toString().replace("-", "");
        OcrTask task = new OcrTask();
        task.setUserId(userId);
        task.setFileType(fileType);
        task.setFileUrl(fileUrl);
        task.setStatus(OcrTaskStatus.PENDING);
        task.setRetryCount(0);
        ocrTaskMapper.insert(task);

        // MVP: save file to temp location
        saveFileMvp(file, fileUrl, task.getId());

        // Update status to PROCESSING
        task.setStatus(OcrTaskStatus.PROCESSING);
        ocrTaskMapper.updateById(task);

        log.info("[OcrTaskService] OcrTask created taskId={} fileType={}", task.getId(), fileType);
        saveOperationLog(task.getUserId(), OperationAction.CREATE, task.getId(),
                String.format("{\"event\":\"CREATE\",\"fileType\":\"%s\"}", fileType));

        // Perform OCR extraction (synchronous in MVP)
        performOcrFromFile(task);

        // Re-read task for latest status
        task = ocrTaskMapper.selectById(task.getId());
        return buildOcrTaskResponse(task);
    }

    @Override
    public OcrTaskResponse getOcrTask(Long taskId) {
        OcrTask task = ocrTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.OCR_TASK_NOT_FOUND);
        }
        Long currentUserId = RequestContextUtil.getCurrentUserId();
        if (!task.getUserId().equals(currentUserId)) {
            throw new BizException(ErrorCode.OCR_TASK_NOT_FOUND);
        }
        return buildOcrTaskResponse(task);
    }

    @Override
    public DebtResponse confirmOcrTask(Long taskId, ConfirmOcrTaskRequest request) {
        OcrTask task = ocrTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.OCR_TASK_NOT_FOUND);
        }
        Long currentUserId = RequestContextUtil.getCurrentUserId();
        if (!task.getUserId().equals(currentUserId)) {
            throw new BizException(ErrorCode.OCR_TASK_NOT_FOUND);
        }

        if (!OcrTaskStatus.SUCCESS.equals(task.getStatus())) {
            log.warn("[OcrTaskService] Cannot confirm task in status={} taskId={}", task.getStatus(), taskId);
            throw new BizException(ErrorCode.OCR_TASK_PROCESSING,
                    "OCR 任务未成功，当前状态: " + task.getStatus());
        }

        // Extract fields from JSON
        OcrExtractedFields fields = parseExtractedFields(task.getExtractedFieldsJson());

        // Build Debt from OCR fields + request corrections
        Debt debt = buildDebtFromOcr(task, fields, request);
        debtMapper.insert(debt);

        // Link task to debt
        task.setDebtId(debt.getId());
        ocrTaskMapper.updateById(task);

        log.info("[OcrTaskService] OcrTask confirmed taskId={} debtId={}", taskId, debt.getId());
        saveOperationLog(task.getUserId(), OperationAction.UPDATE, task.getId(),
                String.format("{\"event\":\"CONFIRM\",\"debtId\":%d}", debt.getId()));

        return buildDebtResponse(debt);
    }

    @Override
    public OcrTaskResponse retryOcrTask(Long taskId) {
        OcrTask task = ocrTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.OCR_TASK_NOT_FOUND);
        }
        Long currentUserId = RequestContextUtil.getCurrentUserId();
        if (!task.getUserId().equals(currentUserId)) {
            throw new BizException(ErrorCode.OCR_TASK_NOT_FOUND);
        }

        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retryCount >= MAX_RETRY_COUNT) {
            log.warn("[OcrTaskService] Retry exceeded taskId={} retryCount={}", taskId, retryCount);
            throw new BizException(ErrorCode.OCR_RETRY_EXCEEDED);
        }

        // Increment retry count and reset status
        task.setRetryCount(retryCount + 1);
        task.setStatus(OcrTaskStatus.PROCESSING);
        task.setErrorMessage(null);
        ocrTaskMapper.updateById(task);

        log.info("[OcrTaskService] Retrying OCR taskId={} retryCount={}", taskId, task.getRetryCount());
        saveOperationLog(task.getUserId(), OperationAction.UPDATE, task.getId(),
                String.format("{\"event\":\"RETRY\",\"retryCount\":%d}", task.getRetryCount()));

        // Re-read file and perform OCR
        // MVP: read from saved file path
        performOcrFromFile(task);

        task = ocrTaskMapper.selectById(taskId);
        return buildOcrTaskResponse(task);
    }

    // ===== Private helpers =====

    private void validateFileFormat(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BizException(ErrorCode.OCR_FILE_FORMAT_INVALID);
        }
        String lower = originalFilename.toLowerCase();
        if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg")
                && !lower.endsWith(".png") && !lower.endsWith(".pdf")) {
            log.warn("[OcrTaskService] Invalid file format: {}", originalFilename);
            throw new BizException(ErrorCode.OCR_FILE_FORMAT_INVALID);
        }
    }

    private void saveFileMvp(MultipartFile file, String fileUrl, Long taskId) {
        try {
            Path dir = Paths.get("/tmp/ocr");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path dest = Paths.get(fileUrl);
            file.transferTo(dest.toFile());
        } catch (IOException e) {
            log.error("[OcrTaskService] Failed to save file taskId={}", taskId, e);
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件保存失败", e);
        }
    }

    private void performOcrFromFile(OcrTask task) {
        try {
            Path filePath = Paths.get(task.getFileUrl());
            if (!Files.exists(filePath)) {
                task.setStatus(OcrTaskStatus.FAILED);
                task.setErrorMessage("文件不存在，请重新上传");
                ocrTaskMapper.updateById(task);
                return;
            }
            byte[] bytes = Files.readAllBytes(filePath);
            String imageBase64 = Base64.getEncoder().encodeToString(bytes);
            doOcrExtract(task, imageBase64);
        } catch (IOException e) {
            log.error("[OcrTaskService] Failed to read file for retry taskId={}", task.getId(), e);
            task.setStatus(OcrTaskStatus.FAILED);
            task.setErrorMessage("文件读取失败: " + e.getMessage());
            ocrTaskMapper.updateById(task);
        }
    }

    private void doOcrExtract(OcrTask task, String imageBase64) {
        try {
            OcrExtractResult result = ocrExtractService.extract(imageBase64, task.getFileType());
            if (result.isSuccess()) {
                String fieldsJson = objectMapper.writeValueAsString(result.getFields());
                task.setExtractedFieldsJson(fieldsJson);
                task.setConfidenceScore(result.getOverallConfidence());
                task.setStatus(OcrTaskStatus.SUCCESS);
                log.info("[OcrTaskService] OCR success taskId={} confidence={}", task.getId(), result.getOverallConfidence());
            } else {
                task.setStatus(OcrTaskStatus.FAILED);
                task.setErrorMessage(result.getErrorMessage());
                log.warn("[OcrTaskService] OCR failed taskId={} error={}", task.getId(), result.getErrorMessage());
            }
        } catch (BizException e) {
            task.setStatus(OcrTaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            log.error("[OcrTaskService] OCR BizException taskId={}", task.getId(), e);
        } catch (JsonProcessingException e) {
            task.setStatus(OcrTaskStatus.FAILED);
            task.setErrorMessage("字段序列化失败");
            log.error("[OcrTaskService] JSON serialization failed taskId={}", task.getId(), e);
        }
        ocrTaskMapper.updateById(task);
    }

    private OcrExtractedFields parseExtractedFields(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OcrExtractedFields.class);
        } catch (JsonProcessingException e) {
            log.warn("[OcrTaskService] Failed to parse extractedFieldsJson", e);
            return null;
        }
    }

    private Debt buildDebtFromOcr(OcrTask task, OcrExtractedFields fields, ConfirmOcrTaskRequest request) {
        Debt debt = new Debt();
        debt.setUserId(task.getUserId());
        debt.setSourceType(DebtSourceType.OCR);
        debt.setOcrTaskId(task.getId());
        debt.setConfidenceScore(task.getConfidenceScore());
        debt.setStatus(DebtStatus.CONFIRMED);

        // Extract from OCR fields (F-01: BigDecimal)
        if (fields != null) {
            if (fields.getCreditor() != null) debt.setCreditor(fields.getCreditor().getValue());
            if (fields.getPrincipal() != null) debt.setPrincipal(fields.getPrincipal().getValue());
            if (fields.getTotalRepayment() != null) debt.setTotalRepayment(fields.getTotalRepayment().getValue());
            if (fields.getNominalRate() != null) debt.setNominalRate(fields.getNominalRate().getValue());
            if (fields.getLoanDays() != null) debt.setLoanDays(fields.getLoanDays().getValue());
            if (fields.getStartDate() != null) debt.setStartDate(fields.getStartDate().getValue());
            if (fields.getEndDate() != null) debt.setEndDate(fields.getEndDate().getValue());
            if (fields.getMonthlyPayment() != null) debt.setMonthlyPayment(fields.getMonthlyPayment().getValue());
        }

        // Apply user corrections from request
        if (request != null && request.getCorrections() != null) {
            applyCorrections(debt, request.getCorrections());
        }

        return debt;
    }

    private void applyCorrections(Debt debt, Map<String, String> corrections) {
        for (Map.Entry<String, String> entry : corrections.entrySet()) {
            try {
                switch (entry.getKey()) {
                    case "creditor" -> debt.setCreditor(entry.getValue());
                    case "principal" -> debt.setPrincipal(new BigDecimal(entry.getValue()));
                    case "totalRepayment" -> debt.setTotalRepayment(new BigDecimal(entry.getValue()));
                    case "nominalRate" -> debt.setNominalRate(new BigDecimal(entry.getValue()));
                    case "monthlyPayment" -> debt.setMonthlyPayment(new BigDecimal(entry.getValue()));
                    default -> log.debug("[OcrTaskService] Ignoring unknown correction key={}", entry.getKey());
                }
            } catch (NumberFormatException e) {
                log.warn("[OcrTaskService] Invalid correction value key={} value={}", entry.getKey(), entry.getValue());
            }
        }
    }

    private OcrTaskResponse buildOcrTaskResponse(OcrTask task) {
        Map<String, Object> extractedFields = null;
        if (task.getExtractedFieldsJson() != null) {
            try {
                extractedFields = objectMapper.readValue(task.getExtractedFieldsJson(),
                        objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
            } catch (JsonProcessingException e) {
                log.warn("[OcrTaskService] Failed to deserialize extractedFieldsJson taskId={}", task.getId(), e);
            }
        }

        String relatedDebt = task.getDebtId() != null ? "debts/" + task.getDebtId() : null;

        return OcrTaskResponse.builder()
                .name("ocr-tasks/" + task.getId())
                .fileType(task.getFileType())
                .status(task.getStatus())
                .confidenceScore(task.getConfidenceScore())
                .extractedFields(extractedFields)
                .errorMessage(task.getErrorMessage())
                .relatedDebt(relatedDebt)
                .build();
    }

    private DebtResponse buildDebtResponse(Debt debt) {
        return DebtResponse.fromEntity(debt);
    }

    private void saveOperationLog(Long userId, OperationAction action, Long targetId, String detailJson) {
        operationLogService.record(userId, OperationModule.AI, action, "OcrTask", targetId, detailJson);
    }
}
