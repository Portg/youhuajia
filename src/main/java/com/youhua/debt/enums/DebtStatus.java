package com.youhua.debt.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum DebtStatus {

    DRAFT("草稿", false),
    SUBMITTED("已提交", false),
    OCR_PROCESSING("OCR识别中", false),
    PENDING_CONFIRM("待确认", false),
    CONFIRMED("已确认", false),
    IN_PROFILE("已纳入画像", false),
    OCR_FAILED("OCR识别失败", false),
    DELETED("已删除（逻辑删除）", true);

    @EnumValue
    private final String value = name();
    private final String description;
    private final boolean terminal;

    DebtStatus(String description, boolean terminal) {
        this.description = description;
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
