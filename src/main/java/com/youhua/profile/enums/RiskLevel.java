package com.youhua.profile.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum RiskLevel {

    LOW("低风险"),
    MEDIUM("中风险"),
    HIGH("高风险"),
    CRITICAL("超高风险");  // V2.0

    @EnumValue
    private final String value = name();
    private final String description;

    RiskLevel(String description) {
        this.description = description;
    }
}
