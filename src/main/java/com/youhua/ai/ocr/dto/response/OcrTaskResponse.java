package com.youhua.ai.ocr.dto.response;

import com.youhua.ai.enums.OcrFileType;
import com.youhua.ai.enums.OcrTaskStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class OcrTaskResponse {

    /** 资源名称: ocr-tasks/{taskId} */
    private String name;
    private OcrFileType fileType;
    private OcrTaskStatus status;
    private BigDecimal confidenceScore;
    /** 提取的结构化字段及置信度 */
    private Map<String, Object> extractedFields;
    private String errorMessage;
    /** 关联的债务资源名称: debts/{debtId} */
    private String relatedDebt;
}
