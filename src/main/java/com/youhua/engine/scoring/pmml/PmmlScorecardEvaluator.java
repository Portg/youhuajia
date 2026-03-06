package com.youhua.engine.scoring.pmml;

import com.youhua.engine.scoring.ScoringEngine.DimensionDetail;
import com.youhua.engine.scoring.ScoringEngine.ScoreInput;
import com.youhua.profile.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.InputField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Wraps jpmml-evaluator to evaluate a PMML Scorecard model.
 * Converts ScoreInput → PMML arguments → evaluate → extract results.
 */
@Slf4j
@Component
public class PmmlScorecardEvaluator {

    private static final int SCORE_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Partial scores per characteristic, keyed by characteristic name.
     * Populated during the most recent evaluation for the calling thread.
     */
    private static final Map<String, String> CHARACTERISTIC_TO_REASON_CODE = Map.of(
            "debtIncomeRatioScore", "DIR",
            "weightedAprScore", "APR",
            "liquidityScore", "LIQ",
            "overdueScore", "OVD",
            "creditStabilityScore", "CST"
    );

    /** Dimension name (for DimensionDetail) mapped from characteristic name */
    private static final Map<String, String> CHARACTERISTIC_TO_DIMENSION = Map.of(
            "debtIncomeRatioScore", "debtIncomeRatio",
            "weightedAprScore", "weightedApr",
            "liquidityScore", "liquidity",
            "overdueScore", "overdue",
            "creditStabilityScore", "creditStability"
    );

    /** Dimension labels in Chinese */
    private static final Map<String, String> DIMENSION_LABELS = Map.of(
            "debtIncomeRatio", "负债收入比",
            "weightedApr", "综合利率",
            "liquidity", "资产流动性",
            "overdue", "逾期情况",
            "creditStability", "信用稳定度"
    );

    /** Weights per dimension (matches PMML partialScore = rawScore × weight) */
    private static final Map<String, BigDecimal> DIMENSION_WEIGHTS = Map.of(
            "debtIncomeRatio", new BigDecimal("0.30"),
            "weightedApr", new BigDecimal("0.25"),
            "liquidity", new BigDecimal("0.15"),
            "overdue", new BigDecimal("0.20"),
            "creditStability", new BigDecimal("0.10")
    );

    /**
     * Result of PMML evaluation.
     */
    public record PmmlEvalResult(
            BigDecimal finalScore,
            List<String> reasonCodes,
            Map<String, BigDecimal> partialScores
    ) {}

    /**
     * Evaluate the PMML scorecard model with the given input.
     */
    public PmmlEvalResult evaluate(Evaluator evaluator, ScoreInput input) {
        Map<String, FieldValue> arguments = prepareArguments(evaluator, input);

        Map<String, ?> results = evaluator.evaluate(arguments);

        BigDecimal finalScore = extractFinalScore(results);
        List<String> reasonCodes = extractReasonCodes(results);
        Map<String, BigDecimal> partialScores = extractPartialScores(results);

        log.debug("PMML evaluation: finalScore={}, reasonCodes={}, partialScores={}",
                finalScore, reasonCodes, partialScores);

        return new PmmlEvalResult(finalScore, reasonCodes, partialScores);
    }

    /**
     * Build dimension details from PMML evaluation result and metadata.
     */
    public List<DimensionDetail> buildDimensions(
            PmmlEvalResult evalResult, ScoreInput input, StrategyMetadata metadata) {

        // If PMML didn't provide partial scores, compute them from input
        Map<String, BigDecimal> partialScores = evalResult.partialScores();
        if (partialScores.isEmpty()) {
            partialScores = computePartialScoresFromInput(input);
        }

        List<DimensionDetail> dimensions = new ArrayList<>();

        for (var entry : CHARACTERISTIC_TO_DIMENSION.entrySet()) {
            String charName = entry.getKey();
            String dimName = entry.getValue();
            String label = DIMENSION_LABELS.getOrDefault(dimName, dimName);
            BigDecimal weight = DIMENSION_WEIGHTS.getOrDefault(dimName, BigDecimal.ZERO);

            BigDecimal weightedScore = partialScores
                    .getOrDefault(charName, BigDecimal.ZERO);

            // Reverse-compute raw dimension score from weighted score
            BigDecimal rawScore = weight.compareTo(BigDecimal.ZERO) > 0
                    ? weightedScore.divide(weight, SCORE_SCALE, ROUNDING)
                    : BigDecimal.ZERO;

            BigDecimal inputValue = getDimensionInputValue(dimName, input);

            // Build explanation from metadata
            String explanation = null;
            String improvementTip = null;
            String reasonCode = CHARACTERISTIC_TO_REASON_CODE.get(charName);
            if (metadata != null && metadata.getReasonCodeMessages() != null && reasonCode != null) {
                StrategyMetadata.ReasonCodeMessage rcMsg = metadata.getReasonCodeMessages().get(reasonCode);
                if (rcMsg != null) {
                    explanation = buildExplanation(rcMsg, dimName, inputValue);
                    improvementTip = rcMsg.getImprovementTip();
                }
            }

            dimensions.add(new DimensionDetail(
                    dimName, label, inputValue,
                    rawScore, weight,
                    weightedScore.setScale(SCORE_SCALE, ROUNDING),
                    explanation, improvementTip));
        }

        return dimensions;
    }

    /**
     * Compute partial scores from input when PMML doesn't expose them directly.
     * These match the default.pmml Scorecard logic exactly.
     */
    private Map<String, BigDecimal> computePartialScoresFromInput(ScoreInput input) {
        Map<String, BigDecimal> scores = new LinkedHashMap<>();

        // debtIncomeRatioScore (weight=0.30)
        BigDecimal dirScore;
        if (input.monthlyIncome() == null || input.monthlyIncome().compareTo(BigDecimal.ZERO) <= 0) {
            dirScore = new BigDecimal("6"); // 20 * 0.30
        } else {
            BigDecimal ratio = input.monthlyPayment().divide(input.monthlyIncome(), 10, ROUNDING);
            if (ratio.compareTo(new BigDecimal("0.30")) <= 0) dirScore = new BigDecimal("27");
            else if (ratio.compareTo(new BigDecimal("0.50")) <= 0) dirScore = new BigDecimal("21");
            else if (ratio.compareTo(new BigDecimal("0.70")) <= 0) dirScore = new BigDecimal("15");
            else if (ratio.compareTo(new BigDecimal("0.90")) <= 0) dirScore = new BigDecimal("9");
            else dirScore = new BigDecimal("3");
        }
        scores.put("debtIncomeRatioScore", dirScore);

        // weightedAprScore (weight=0.25)
        BigDecimal aprScore;
        if (input.weightedApr() == null) {
            aprScore = new BigDecimal("13.75"); // mid-range
        } else {
            BigDecimal apr = input.weightedApr();
            if (apr.compareTo(new BigDecimal("10")) <= 0) aprScore = new BigDecimal("22.5");
            else if (apr.compareTo(new BigDecimal("18")) <= 0) aprScore = new BigDecimal("18.75");
            else if (apr.compareTo(new BigDecimal("24")) <= 0) aprScore = new BigDecimal("13.75");
            else if (apr.compareTo(new BigDecimal("36")) <= 0) aprScore = new BigDecimal("8.75");
            else aprScore = new BigDecimal("3.75");
        }
        scores.put("weightedAprScore", aprScore);

        // liquidityScore (weight=0.15)
        BigDecimal liqScore;
        if (input.monthlyIncome() == null || input.monthlyIncome().compareTo(BigDecimal.ZERO) <= 0) {
            liqScore = new BigDecimal("6");
        } else if (input.monthlyIncome().compareTo(input.monthlyPayment()) > 0) {
            liqScore = new BigDecimal("9");
        } else {
            liqScore = new BigDecimal("4.5");
        }
        scores.put("liquidityScore", liqScore);

        // overdueScore (weight=0.20)
        BigDecimal ovdScore;
        int oc = input.overdueCount();
        int md = input.maxOverdueDays();
        if (oc == 0) ovdScore = new BigDecimal("19");
        else if (oc == 1 && md <= 30) ovdScore = new BigDecimal("14");
        else if (oc <= 2 && md <= 60) ovdScore = new BigDecimal("10");
        else if (oc <= 3 && md <= 90) ovdScore = new BigDecimal("6");
        else ovdScore = new BigDecimal("2");
        scores.put("overdueScore", ovdScore);

        // creditStabilityScore (weight=0.10)
        BigDecimal cstScore;
        if (input.debtCount() <= 2 && input.avgLoanDays() >= 180) cstScore = new BigDecimal("8");
        else if (input.debtCount() <= 4) cstScore = new BigDecimal("6");
        else if (input.debtCount() <= 6) cstScore = new BigDecimal("4");
        else cstScore = new BigDecimal("2");
        scores.put("creditStabilityScore", cstScore);

        return scores;
    }

    /**
     * Map final score to RiskLevel using metadata boundaries.
     */
    public RiskLevel mapRiskLevel(BigDecimal finalScore, StrategyMetadata metadata) {
        List<BigDecimal> boundaries = metadata != null && metadata.getRiskLevelBoundaries() != null
                ? metadata.getRiskLevelBoundaries()
                : List.of(new BigDecimal("80"), new BigDecimal("60"), new BigDecimal("40"));

        if (finalScore.compareTo(boundaries.get(0)) >= 0) return RiskLevel.LOW;
        if (finalScore.compareTo(boundaries.get(1)) >= 0) return RiskLevel.MEDIUM;
        if (finalScore.compareTo(boundaries.get(2)) >= 0) return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }

    /**
     * Get restructure threshold from metadata.
     */
    public BigDecimal getRestructureThreshold(StrategyMetadata metadata) {
        if (metadata != null && metadata.getRestructureThreshold() != null) {
            return metadata.getRestructureThreshold();
        }
        return new BigDecimal("60");
    }

    // ===================== Private helpers =====================

    private Map<String, FieldValue> prepareArguments(Evaluator evaluator, ScoreInput input) {
        // Compute debtIncomeRatio
        Double debtIncomeRatio = null;
        if (input.monthlyIncome() != null && input.monthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
            debtIncomeRatio = input.monthlyPayment()
                    .divide(input.monthlyIncome(), 10, ROUNDING)
                    .doubleValue();
        }

        Map<String, Object> rawValues = new LinkedHashMap<>();
        rawValues.put("debtIncomeRatio", debtIncomeRatio);
        rawValues.put("weightedApr", input.weightedApr() != null ? input.weightedApr().doubleValue() : null);
        rawValues.put("monthlyIncome", input.monthlyIncome() != null ? input.monthlyIncome().doubleValue() : null);
        rawValues.put("monthlyPayment", input.monthlyPayment().doubleValue());
        rawValues.put("overdueCount", input.overdueCount());
        rawValues.put("maxOverdueDays", input.maxOverdueDays());
        rawValues.put("debtCount", input.debtCount());
        rawValues.put("avgLoanDays", (int) input.avgLoanDays());

        Map<String, FieldValue> arguments = new LinkedHashMap<>();
        List<InputField> inputFields = evaluator.getInputFields();

        for (InputField inputField : inputFields) {
            String fieldName = inputField.getName();
            Object rawValue = rawValues.get(fieldName);
            FieldValue fieldValue = inputField.prepare(rawValue);
            arguments.put(fieldName, fieldValue);
        }

        return arguments;
    }

    private BigDecimal extractFinalScore(Map<String, ?> results) {
        Object scoreValue = results.get("finalScore");
        if (scoreValue == null) {
            // Try target field
            for (var entry : results.entrySet()) {
                if (entry.getValue() instanceof Number num) {
                    return new BigDecimal(num.toString()).setScale(SCORE_SCALE, ROUNDING);
                }
            }
            return BigDecimal.ZERO;
        }
        if (scoreValue instanceof Number num) {
            return new BigDecimal(num.toString()).setScale(SCORE_SCALE, ROUNDING);
        }
        return new BigDecimal(scoreValue.toString()).setScale(SCORE_SCALE, ROUNDING);
    }

    private List<String> extractReasonCodes(Map<String, ?> results) {
        List<String> codes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Object rc = results.get("ReasonCode" + i);
            if (rc != null && !rc.toString().isEmpty()) {
                codes.add(rc.toString());
            }
        }
        return codes;
    }

    private Map<String, BigDecimal> extractPartialScores(Map<String, ?> results) {
        Map<String, BigDecimal> partialScores = new LinkedHashMap<>();
        for (String charName : CHARACTERISTIC_TO_DIMENSION.keySet()) {
            Object val = results.get(charName);
            if (val instanceof Number num) {
                partialScores.put(charName, new BigDecimal(num.toString()).setScale(SCORE_SCALE, ROUNDING));
            }
        }

        // If partial scores not in results, try to compute from finalScore
        if (partialScores.isEmpty()) {
            log.debug("Partial scores not available in PMML result, dimension breakdown may be limited");
        }

        return partialScores;
    }

    private BigDecimal getDimensionInputValue(String dimName, ScoreInput input) {
        return switch (dimName) {
            case "debtIncomeRatio" -> {
                if (input.monthlyIncome() != null && input.monthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
                    yield input.monthlyPayment().divide(input.monthlyIncome(), 4, ROUNDING);
                }
                yield null;
            }
            case "weightedApr" -> input.weightedApr();
            case "liquidity" -> null;
            case "overdue" -> new BigDecimal(input.overdueCount());
            case "creditStability" -> new BigDecimal(input.debtCount());
            default -> null;
        };
    }

    private String buildExplanation(StrategyMetadata.ReasonCodeMessage rcMsg,
                                     String dimName, BigDecimal inputValue) {
        if (rcMsg.getExplainTemplate() == null || inputValue == null) {
            return null;
        }

        String levelDesc = "";
        if (rcMsg.getLevelDescriptions() != null) {
            for (StrategyMetadata.LevelDescription ld : rcMsg.getLevelDescriptions()) {
                if (inputValue.compareTo(ld.getMax()) <= 0) {
                    levelDesc = ld.getDesc();
                    break;
                }
            }
        }

        return rcMsg.getExplainTemplate()
                .replace("{value}", inputValue.toPlainString())
                .replace("{level_desc}", levelDesc);
    }
}
