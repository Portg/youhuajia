package com.youhua.engine.scoring.pmml;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * POJO for .meta.yml strategy metadata.
 */
@Data
public class StrategyMetadata {

    private String strategyName;
    private String segment;
    private String description;
    private String createdBy;
    private String createdAt;
    private String version;

    private List<BigDecimal> riskLevelBoundaries;
    private BigDecimal restructureThreshold;

    private Map<String, ReasonCodeMessage> reasonCodeMessages;

    @Data
    public static class ReasonCodeMessage {
        private String label;
        private String explainTemplate;
        private List<LevelDescription> levelDescriptions;
        private String improvementTip;
    }

    @Data
    public static class LevelDescription {
        private BigDecimal max;
        private String desc;
    }
}
