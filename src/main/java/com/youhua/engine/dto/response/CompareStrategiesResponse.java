package com.youhua.engine.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "策略对比响应")
public class CompareStrategiesResponse {

    @Schema(description = "策略 A 评分结果")
    private StrategyScoreSummary strategyA;

    @Schema(description = "策略 B 评分结果")
    private StrategyScoreSummary strategyB;

    @Schema(description = "分数差值 (A - B)")
    private BigDecimal scoreDelta;

    @Data
    @Builder
    public static class StrategyScoreSummary {
        private String segment;
        private String strategyName;
        private String version;
        private BigDecimal finalScore;
        private String riskLevel;
        private String recommendation;
    }
}
