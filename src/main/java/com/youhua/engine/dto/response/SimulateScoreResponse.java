package com.youhua.engine.dto.response;

import com.youhua.engine.scoring.ScoringEngine;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "What-if 评分模拟响应")
public class SimulateScoreResponse {

    @Schema(description = "当前评分结果")
    private ScoreSummary current;

    @Schema(description = "模拟评分结果")
    private ScoreSummary simulated;

    @Schema(description = "各维度变化量")
    private List<DimensionDelta> dimensionDeltas;

    @Data
    @Builder
    public static class ScoreSummary {
        private BigDecimal finalScore;
        private String riskLevel;
        private String recommendation;
        private List<ScoringEngine.DimensionDetail> dimensions;
    }

    @Data
    @Builder
    public static class DimensionDelta {
        private String name;
        private String label;
        private BigDecimal currentWeightedScore;
        private BigDecimal simulatedWeightedScore;
        private BigDecimal delta;
    }
}
