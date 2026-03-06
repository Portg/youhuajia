package com.youhua.debt.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum OverdueStatus {

    NORMAL("正常"),
    OVERDUE_30("逾期30天内"),
    OVERDUE_60("逾期60天内"),
    OVERDUE_90_PLUS("逾期90天以上");

    @EnumValue
    private final String value = name();
    private final String description;

    OverdueStatus(String description) {
        this.description = description;
    }
}
