package com.youhua.engine.scoring;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * POJO for preaudit.meta.yml strategy metadata.
 */
@Data
public class PreAuditMetadata {

    private String strategyName;
    private String description;
    private String version;

    private int baseProbability;
    private int minProbability;
    private int maxProbability;

    private Map<String, Dimension> dimensions;
    private List<SuggestionRule> suggestions;
    private String fallbackSuggestion;

    @Data
    public static class Dimension {
        private String label;
        private List<Threshold> thresholds;

        // OVD 专用
        private Integer noOverdueDelta;
        private Integer hasOverdueDelta;

        // APR_HIGH 专用
        private BigDecimal highAprThreshold;
    }

    @Data
    public static class Threshold {
        private BigDecimal min;
        private BigDecimal max;
        private int delta;
    }

    @Data
    public static class SuggestionRule {
        private String condition;
        private String text;
    }
}
