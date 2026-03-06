package com.youhua.auth.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum UserStatus {

    ACTIVE("正常使用", false),
    FROZEN("已冻结", false),
    CANCELLING("注销冷静期（30天）", false),
    CANCELLED("已注销，数据已删除", true);

    @EnumValue
    private final String value = name();
    private final String description;
    private final boolean terminal;

    UserStatus(String description, boolean terminal) {
        this.description = description;
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
