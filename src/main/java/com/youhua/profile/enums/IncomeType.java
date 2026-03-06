package com.youhua.profile.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum IncomeType {

    SALARY("工资"),
    BUSINESS("经营收入"),
    FREELANCE("自由职业"),
    INVESTMENT("投资收益"),
    OTHER("其他");

    @EnumValue
    private final String value = name();
    private final String description;

    IncomeType(String description) {
        this.description = description;
    }
}
