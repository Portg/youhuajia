package com.youhua.debt.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum DebtSourceType {

    MANUAL("手动录入"),
    OCR("OCR识别"),
    BANK_API("银行API"),        // V2.0
    CREDIT_REPORT("征信报告");  // V2.0

    @EnumValue
    private final String value = name();
    private final String description;

    DebtSourceType(String description) {
        this.description = description;
    }
}
