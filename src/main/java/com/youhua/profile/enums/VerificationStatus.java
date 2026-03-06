package com.youhua.profile.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum VerificationStatus {

    UNVERIFIED("未验证"),
    VERIFIED("已验证");

    @EnumValue
    private final String value = name();
    private final String description;

    VerificationStatus(String description) {
        this.description = description;
    }
}
