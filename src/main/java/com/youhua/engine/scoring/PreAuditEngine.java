package com.youhua.engine.scoring;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pre-audit probability estimation engine.
 * Reads rules from preaudit.meta.yml (same pattern as scoring PMML strategies).
 * All calculations are deterministic — no LLM calls (F-02).
 *
 * <p>Input: user's score, debt-income ratio, overdue status, debt count, high-APR ratio.
 * Output: estimated probability (35-92%) + personalized suggestions (max 3).
 */
@Slf4j
@Component
public class PreAuditEngine {

    private static final String CONFIG_PATH = "strategies/preaudit.meta.yml";

    @Getter
    private volatile PreAuditMetadata config;

    @PostConstruct
    public void init() {
        loadConfig();
    }

    private void loadConfig() {
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_PATH)) {
                if (is == null) {
                    log.error("[PreAuditEngine] Config not found: {}", CONFIG_PATH);
                    return;
                }
                config = yamlMapper.readValue(is, PreAuditMetadata.class);
                log.info("[PreAuditEngine] Loaded config: version={}", config.getVersion());
            }
        } catch (Exception e) {
            log.error("[PreAuditEngine] Failed to load config", e);
        }
    }

    /**
     * Estimate pre-audit approval probability.
     *
     * @param input scoring and debt metrics
     * @return result with probability and suggestions
     */
    public PreAuditResult estimate(PreAuditInput input) {
        if (config == null) {
            log.warn("[PreAuditEngine] Config not loaded, returning default");
            return new PreAuditResult(50, List.of("准备详细的收入证明和还款记录可提高通过率"));
        }

        int probability = config.getBaseProbability();
        Map<String, PreAuditMetadata.Dimension> dims = config.getDimensions();

        // SCORE dimension
        probability += matchThresholdMin(dims.get("SCORE"), input.score());

        // DIR (debt-income ratio) dimension
        probability += matchThresholdMax(dims.get("DIR"), input.debtIncomeRatio());

        // OVD (overdue) dimension
        PreAuditMetadata.Dimension ovd = dims.get("OVD");
        if (ovd != null) {
            probability += input.hasOverdue()
                    ? ovd.getHasOverdueDelta()
                    : ovd.getNoOverdueDelta();
        }

        // CST (debt count) dimension
        probability += matchThresholdMax(dims.get("CST"), new BigDecimal(input.debtCount()));

        // APR_HIGH (high APR ratio) dimension
        probability += matchThresholdMax(dims.get("APR_HIGH"), input.highAprRatio());

        // Clamp
        probability = Math.max(config.getMinProbability(),
                Math.min(config.getMaxProbability(), probability));

        // Suggestions
        List<String> suggestions = buildSuggestions(input);

        log.debug("[PreAuditEngine] estimate: score={}, dir={}, overdue={}, debtCount={}, highAprRatio={} → probability={}",
                input.score(), input.debtIncomeRatio(), input.hasOverdue(),
                input.debtCount(), input.highAprRatio(), probability);

        return new PreAuditResult(probability, suggestions);
    }

    private int matchThresholdMin(PreAuditMetadata.Dimension dim, BigDecimal value) {
        if (dim == null || dim.getThresholds() == null || value == null) return 0;
        for (PreAuditMetadata.Threshold t : dim.getThresholds()) {
            if (t.getMin() != null && value.compareTo(t.getMin()) >= 0) {
                return t.getDelta();
            }
        }
        return 0;
    }

    private int matchThresholdMax(PreAuditMetadata.Dimension dim, BigDecimal value) {
        if (dim == null || dim.getThresholds() == null || value == null) return 0;
        for (PreAuditMetadata.Threshold t : dim.getThresholds()) {
            if (t.getMax() != null && value.compareTo(t.getMax()) <= 0) {
                return t.getDelta();
            }
        }
        return 0;
    }

    private List<String> buildSuggestions(PreAuditInput input) {
        List<String> result = new ArrayList<>();

        for (PreAuditMetadata.SuggestionRule rule : config.getSuggestions()) {
            if (result.size() >= 3) break;
            if (matchCondition(rule.getCondition(), input)) {
                result.add(rule.getText());
            }
        }

        if (result.isEmpty()) {
            result.add(config.getFallbackSuggestion());
        }
        return result;
    }

    private boolean matchCondition(String condition, PreAuditInput input) {
        return switch (condition) {
            case "HAS_OVERDUE" -> input.hasOverdue();
            case "HIGH_APR_RATIO_GT_30" -> input.highAprRatio().compareTo(new BigDecimal("0.30")) > 0;
            case "DIR_GT_70" -> input.debtIncomeRatio().compareTo(new BigDecimal("0.70")) > 0;
            case "DEBT_COUNT_GT_4" -> input.debtCount() > 4;
            case "SCORE_GE_70" -> input.score().compareTo(new BigDecimal("70")) >= 0;
            case "LOW_DIR_NO_OVERDUE" -> input.debtIncomeRatio().compareTo(new BigDecimal("0.50")) <= 0
                    && !input.hasOverdue();
            case "SCORE_LT_50" -> input.score().compareTo(new BigDecimal("50")) < 0;
            default -> {
                log.warn("[PreAuditEngine] Unknown condition: {}", condition);
                yield false;
            }
        };
    }

    // ==================== DTOs ====================

    public record PreAuditInput(
            BigDecimal score,
            BigDecimal debtIncomeRatio,
            boolean hasOverdue,
            int debtCount,
            BigDecimal highAprRatio
    ) {}

    public record PreAuditResult(
            int probability,
            List<String> suggestions
    ) {}
}
