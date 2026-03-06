package com.youhua.ai.ocr.controller;

import com.youhua.ai.enums.OcrFileType;
import com.youhua.ai.ocr.dto.request.ConfirmOcrTaskRequest;
import com.youhua.ai.ocr.dto.response.OcrTaskResponse;
import com.youhua.ai.ocr.service.OcrTaskService;
import com.youhua.debt.dto.response.DebtResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "ocr-tasks", description = "OCR 识别任务资源")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OcrTaskController {

    private final OcrTaskService ocrTaskService;

    @Operation(summary = "Create - 上传文件创建 OCR 任务")
    @PostMapping(value = "/ocr-tasks", consumes = "multipart/form-data")
    public OcrTaskResponse createOcrTask(@RequestPart("file") MultipartFile file,
                                         @RequestParam OcrFileType fileType) {
        return ocrTaskService.createOcrTask(file, fileType);
    }

    @Operation(summary = "Get - 查询 OCR 任务结果")
    @GetMapping("/ocr-tasks/{taskId}")
    public OcrTaskResponse getOcrTask(@PathVariable Long taskId) {
        return ocrTaskService.getOcrTask(taskId);
    }

    @Operation(summary = "确认 OCR 结果并生成债务记录")
    @PostMapping("/ocr-tasks/{taskId}:confirm")
    public DebtResponse confirmOcrTask(@PathVariable Long taskId,
                                       @Valid @RequestBody ConfirmOcrTaskRequest request) {
        return ocrTaskService.confirmOcrTask(taskId, request);
    }

    @Operation(summary = "重试失败的 OCR 任务")
    @PostMapping("/ocr-tasks/{taskId}:retry")
    public OcrTaskResponse retryOcrTask(@PathVariable Long taskId) {
        return ocrTaskService.retryOcrTask(taskId);
    }
}
