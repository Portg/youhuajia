package com.youhua.ai.ocr;

import com.youhua.ai.enums.OcrTaskStatus;
import com.youhua.ai.ocr.entity.OcrTask;
import com.youhua.ai.ocr.mapper.OcrTaskMapper;
import com.youhua.ai.ocr.service.OcrExtractService;
import com.youhua.infra.log.OperationLogService;
import com.youhua.infra.log.enums.OperationAction;
import com.youhua.infra.log.enums.OperationModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * OCR 任务状态机 Action 执行逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrTaskAction {

    private final OcrTaskMapper ocrTaskMapper;
    private final OperationLogService operationLogService;
    private final OcrExtractService ocrExtractService;

    /**
     * START_PROCESS 事件 Action：callDeepSeekOcr()。
     * 调用 DeepSeek OCR 服务进行图片/文档识别。
     */
    public void callDeepSeekOcr(OcrTask task) {
        log.debug("[OcrTaskAction] callDeepSeekOcr taskId={} fileUrl={}", task.getId(), task.getFileUrl());
        saveOperationLog(task.getUserId(), OperationAction.CREATE, task.getId(),
                String.format("{\"event\":\"START_PROCESS\",\"fileUrl\":\"%s\"}", task.getFileUrl()));
        // Delegate OCR extraction to OcrExtractService
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(task.getFileUrl()));
            String imageBase64 = Base64.getEncoder().encodeToString(bytes);
            var result = ocrExtractService.extract(imageBase64, task.getFileType());
            if (result.isSuccess()) {
                log.debug("[OcrTaskAction] callDeepSeekOcr success taskId={} confidence={}",
                        task.getId(), result.getOverallConfidence());
            } else {
                log.warn("[OcrTaskAction] callDeepSeekOcr failed taskId={} error={}",
                        task.getId(), result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("[OcrTaskAction] callDeepSeekOcr error taskId={}", task.getId(), e);
        }
    }

    /**
     * EXTRACT_SUCCESS 事件 Action：saveExtractedFields()。
     * 持久化 OCR 提取的结构化字段。
     */
    public void saveExtractedFields(OcrTask task, String extractedFieldsJson) {
        log.debug("[OcrTaskAction] saveExtractedFields taskId={}", task.getId());
        task.setExtractedFieldsJson(extractedFieldsJson);
        task.setStatus(OcrTaskStatus.SUCCESS);
        ocrTaskMapper.updateById(task);
        saveOperationLog(task.getUserId(), OperationAction.UPDATE, task.getId(),
                "{\"event\":\"EXTRACT_SUCCESS\",\"desc\":\"OCR提取字段已保存\"}");
    }

    /**
     * EXTRACT_FAIL 事件 Action：saveErrorMessage()。
     */
    public void saveErrorMessage(OcrTask task, String errorMessage) {
        log.warn("[OcrTaskAction] saveErrorMessage taskId={} error={}", task.getId(), errorMessage);
        task.setErrorMessage(errorMessage);
        task.setStatus(OcrTaskStatus.FAILED);
        ocrTaskMapper.updateById(task);
        saveOperationLog(task.getUserId(), OperationAction.UPDATE, task.getId(),
                String.format("{\"event\":\"EXTRACT_FAIL\",\"error\":\"%s\"}", errorMessage));
    }

    /**
     * RETRY 事件 Action：incrementRetryCount()。
     */
    public void incrementRetryCount(OcrTask task) {
        int newRetryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        task.setRetryCount(newRetryCount);
        task.setStatus(OcrTaskStatus.PENDING);
        ocrTaskMapper.updateById(task);

        log.debug("[OcrTaskAction] incrementRetryCount taskId={} retryCount={}", task.getId(), newRetryCount);
        saveOperationLog(task.getUserId(), OperationAction.UPDATE, task.getId(),
                String.format("{\"event\":\"RETRY\",\"retryCount\":%d}", newRetryCount));
    }

    /**
     * 超时 Action（PROCESSING → FAILED）：saveTimeoutError()。
     */
    public void saveTimeoutError(OcrTask task) {
        log.warn("[OcrTaskAction] saveTimeoutError taskId={} 超时（60s）", task.getId());
        task.setErrorMessage("OCR 识别超时（60秒），自动标记为失败");
        task.setStatus(OcrTaskStatus.FAILED);
        ocrTaskMapper.updateById(task);
        saveOperationLog(task.getUserId(), OperationAction.UPDATE, task.getId(),
                "{\"event\":\"TIMEOUT\",\"desc\":\"OCR识别超时（60s），自动失败\"}");
    }

    /**
     * 更新 OCR 任务状态（状态机驱动，统一入口）。
     */
    public void updateStatus(OcrTask task, OcrTaskStatus newStatus) {
        log.debug("[OcrTaskAction] updateStatus taskId={} {} -> {}", task.getId(), task.getStatus(), newStatus);
        task.setStatus(newStatus);
        ocrTaskMapper.updateById(task);
    }

    private void saveOperationLog(Long userId, OperationAction action, Long targetId, String detailJson) {
        operationLogService.record(userId, OperationModule.AI, action, "OcrTask", targetId, detailJson);
    }
}
