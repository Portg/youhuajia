package com.youhua.profile.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 画像生成流程状态枚举。
 * 状态流转由状态机驱动，不得直接 set 状态值。
 */
@Getter
public enum ProfileGenerationStatus {

    IDLE("空闲，等待触发", false),
    VALIDATING("数据校验中", false),
    CALCULATING("计算中（APR + 加权利率 + 评分）", false),
    GENERATING_SUGGESTION("AI 生成优化建议中", false),
    COMPLETED("生成完成（含AI建议）", true),
    COMPLETED_WITHOUT_AI("生成完成（AI建议失败，仅展示数据+评分）", true),
    FAILED("生成失败", false);

    @EnumValue
    private final String value = name();
    private final String description;
    private final boolean terminal;

    ProfileGenerationStatus(String description, boolean terminal) {
        this.description = description;
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
