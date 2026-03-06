package com.youhua.engine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "What-if 评分模拟请求")
public class SimulateScoreRequest {

    @Schema(description = "模拟操作列表")
    private List<SimulateAction> actions;

    @Data
    @Schema(description = "单个模拟操作")
    public static class SimulateAction {

        @Schema(description = "操作类型: PAYOFF（还清）, REDUCE_PRINCIPAL（减少本金）, REPLACE_RATE（置换利率）")
        @NotNull
        private ActionType type;

        @Schema(description = "目标债务 ID")
        @NotNull
        private Long debtId;

        @Schema(description = "新值（REDUCE_PRINCIPAL 的减少金额 / REPLACE_RATE 的新利率）")
        private BigDecimal value;
    }

    public enum ActionType {
        PAYOFF,
        REDUCE_PRINCIPAL,
        REPLACE_RATE
    }
}
