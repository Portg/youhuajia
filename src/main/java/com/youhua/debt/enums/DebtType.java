package com.youhua.debt.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum DebtType {

    CREDIT_CARD("信用卡"),
    CONSUMER_LOAN("消费贷"),
    BUSINESS_LOAN("经营贷"),
    MORTGAGE("房贷"),
    OTHER("其他");

    @EnumValue
    private final String value = name();
    private final String description;

    DebtType(String description) {
        this.description = description;
    }
}
