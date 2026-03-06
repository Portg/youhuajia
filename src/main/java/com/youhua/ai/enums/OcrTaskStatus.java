package com.youhua.ai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum OcrTaskStatus {

    PENDING("等待处理", false),
    PROCESSING("识别处理中", false),
    SUCCESS("识别成功", true),
    FAILED("识别失败", false);

    @EnumValue
    private final String value = name();
    private final String description;
    private final boolean terminal;

    OcrTaskStatus(String description, boolean terminal) {
        this.description = description;
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
