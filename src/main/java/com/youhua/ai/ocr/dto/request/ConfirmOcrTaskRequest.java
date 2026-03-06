package com.youhua.ai.ocr.dto.request;

import lombok.Data;

import java.util.Map;

@Data
public class ConfirmOcrTaskRequest {

    /** 用户修正的字段 key-value */
    private Map<String, String> corrections;
}
