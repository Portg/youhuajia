package com.youhua.engine.scoring.pmml;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * User segments for strategy routing.
 * Each segment maps to a PMML file in the strategies directory.
 */
@Getter
public enum UserSegment {

    HIGH_DEBT("HIGH_DEBT", "high-debt", "高负债用户"),
    MORTGAGE_HEAVY("MORTGAGE_HEAVY", "mortgage-heavy", "房贷为主用户"),
    YOUNG_BORROWER("YOUNG_BORROWER", "young-borrower", "年轻首贷用户"),
    DEFAULT("DEFAULT", "default", "通用用户");

    @EnumValue
    private final String value;
    private final String pmmlFileName;
    private final String label;

    UserSegment(String value, String pmmlFileName, String label) {
        this.value = value;
        this.pmmlFileName = pmmlFileName;
        this.label = label;
    }
}
