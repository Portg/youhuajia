package com.youhua.infra.log.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum OperationAction {

    CREATE,
    UPDATE,
    DELETE,
    CALCULATE,
    GENERATE;

    @EnumValue
    private final String value = name();
}
