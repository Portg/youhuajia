package com.youhua.ai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum OcrFileType {

    CONTRACT("合同"),
    BILL("账单"),
    SMS_SCREENSHOT("短信截图");

    @EnumValue
    private final String value = name();
    private final String description;

    OcrFileType(String description) {
        this.description = description;
    }
}
