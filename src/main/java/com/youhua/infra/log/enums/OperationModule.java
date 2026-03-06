package com.youhua.infra.log.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum OperationModule {

    DEBT,
    PROFILE,
    ENGINE,
    AI,
    AUTH;

    @EnumValue
    private final String value = name();
}
