package com.youhua.ai.ocr.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.youhua.ai.enums.OcrFileType;
import com.youhua.ai.enums.OcrTaskStatus;
import com.youhua.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@TableName("t_ocr_task")
public class OcrTask extends BaseEntity {

    private Long userId;

    private String fileUrl;

    private OcrFileType fileType;

    private OcrTaskStatus status;

    private String rawResultJson;

    private String extractedFieldsJson;

    private BigDecimal confidenceScore;

    private String errorMessage;

    private Integer retryCount;

    private Long debtId;
}
