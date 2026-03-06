package com.youhua.ai.ocr.service;

import com.youhua.ai.enums.OcrFileType;
import com.youhua.ai.ocr.dto.request.ConfirmOcrTaskRequest;
import com.youhua.ai.ocr.dto.response.OcrTaskResponse;
import com.youhua.debt.dto.response.DebtResponse;
import org.springframework.web.multipart.MultipartFile;

public interface OcrTaskService {

    OcrTaskResponse createOcrTask(MultipartFile file, OcrFileType fileType);

    OcrTaskResponse getOcrTask(Long taskId);

    DebtResponse confirmOcrTask(Long taskId, ConfirmOcrTaskRequest request);

    OcrTaskResponse retryOcrTask(Long taskId);
}
