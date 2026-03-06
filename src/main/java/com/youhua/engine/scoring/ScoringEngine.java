package com.youhua.engine.scoring;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.engine.scoring.pmml.PmmlScorecardEvaluator;
import com.youhua.engine.scoring.pmml.PmmlStrategyRegistry;
import com.youhua.engine.scoring.pmml.PmmlStrategyRegistry.StrategyEntry;
import com.youhua.engine.scoring.pmml.StrategyMetadata;
import com.youhua.engine.scoring.pmml.UserSegment;
import com.youhua.engine.scoring.pmml.UserSegmentMatcher;
import com.youhua.profile.enums.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Five-dimensional debt restructure feasibility scoring engine.
 *
 * <p>Score range: 0-100 (BigDecimal, scale=2).
 * Primary evaluation via PMML Scorecard; falls back to hardcoded logic if PMML unavailable.
 * All amounts use BigDecimal (F-01). Does NOT call LLM (F-02).
 *
 * <p>Critical constraint: score &lt; 60 maps to {@link Recommendation#CREDIT_BUILDING},
 * never to a rejection message. (CLAUDE.md F-13, user-journey.md Section 5)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoringEngine {

    private static final int SCORE_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    // ===== Fallback constants (hardcoded defaults, used only when PMML unavailable) =====

    private static final BigDecimal W_DIR = new BigDecimal("0.30");
    private static final BigDecimal W_APR = new BigDecimal("0.25");
    private static final BigDecimal W_LIQ = new BigDecimal("0.15");
    private static final BigDecimal W_OVD = new BigDecimal("0.20");
    private static final BigDecimal W_CST = new BigDecimal("0.10");

    private static final List<BigDecimal> DIR_LEVELS = List.of(
            new BigDecimal("0.30"), new BigDecimal("0.50"),
            new BigDecimal("0.70"), new BigDecimal("0.90"));
    private static final List<BigDecimal> DIR_SCORES = List.of(
            new BigDecimal("90"), new BigDecimal("70"),
            new BigDecimal("50"), new BigDecimal("30"),
            new BigDecimal("10"));

    private static final List<BigDecimal> APR_LEVELS = List.of(
            new BigDecimal("10.0"), new BigDecimal("18.0"),
            new BigDecimal("24.0"), new BigDecimal("36.0"));
    private static final List<BigDecimal> APR_SCORES = List.of(
            new BigDecimal("90"), new BigDecimal("75"),
            new BigDecimal("55"), new BigDecimal("35"),
            new BigDecimal("15"));

    private static final List<BigDecimal> RISK_BOUNDARIES = List.of(
            new BigDecimal("80"), new BigDecimal("60"), new BigDecimal("40"));
    private static final BigDecimal RESTRUCTURE_THRESHOLD = new BigDecimal("60");

    private static final int OVD_LEVEL1_MAX_DAYS = 30;
    private static final int OVD_LEVEL2_MAX_DAYS = 60;
    private static final int OVD_LEVEL3_MAX_DAYS = 90;

    private static final int CST_LOW_DEBT_COUNT = 2;
    private static final int CST_MED_DEBT_COUNT = 4;
    private static final int CST_HIGH_DEBT_COUNT = 6;
    private static final long CST_MIN_AVG_LOAN_DAYS = 180;

    // ===== Dependencies =====

    private final PmmlStrategyRegistry strategyRegistry;
    private final PmmlScorecardEvaluator pmmlEvaluator;
    private final UserSegmentMatcher segmentMatcher;

    /**
     * Compute the restructure feasibility score for the given user profile.
     * Delegates to PMML when available, falls back to hardcoded logic.
     *
     * @param input scoring input data
     * @return full score result with dimension breakdown
     * @throws BizException ENGINE_SCORE_FAILED on calculation error
     */
    public ScoreResult score(ScoreInput input) {
        return score(input, null);
    }

    /**
     * Score with explicit mortgage ratio for segment matching.
     */
    public ScoreResult score(ScoreInput input, BigDecimal mortgageRatio) {
        if (input == null) {
            throw new BizException(ErrorCode.ENGINE_SCORE_FAILED, "Score input must not be null");
        }

        try {
            if (strategyRegistry.isInitialized()) {
                return scoreWithPmml(input, mortgageRatio);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("PMML scoring failed, falling back to hardcoded logic", e);
        }

        // Fallback to hardcoded scoring
        return doScore(input);
    }

    /**
     * PMML-based scoring.
     */
    private ScoreResult scoreWithPmml(ScoreInput input, BigDecimal mortgageRatio) {
        UserSegment segment = mortgageRatio != null
                ? segmentMatcher.match(input, mortgageRatio)
                : segmentMatcher.match(input);

        StrategyEntry entry = strategyRegistry.getStrategy(segment);
        StrategyMetadata metadata = entry.metadata();

        PmmlScorecardEvaluator.PmmlEvalResult evalResult =
                pmmlEvaluator.evaluate(entry.evaluator(), input);

        BigDecimal finalScore = evalResult.finalScore();
        BigDecimal threshold = pmmlEvaluator.getRestructureThreshold(metadata);
        RiskLevel riskLevel = pmmlEvaluator.mapRiskLevel(finalScore, metadata);
        Recommendation recommendation = mapRecommendation(finalScore, threshold);
        String message = buildMessage(recommendation);
        String nextPage = buildNextPage(recommendation);

        List<DimensionDetail> dimensions = pmmlEvaluator.buildDimensions(evalResult, input, metadata);

        log.debug("PMML score result: segment={}, strategy={}, finalScore={}, riskLevel={}, recommendation={}",
                segment, metadata != null ? metadata.getStrategyName() : "unknown",
                finalScore, riskLevel, recommendation);

        return new ScoreResult(finalScore, riskLevel, recommendation, message, nextPage,
                dimensions, LocalDateTime.now(),
                segment, metadata != null ? metadata.getStrategyName() : null,
                metadata != null ? metadata.getVersion() : null,
                evalResult.reasonCodes());
    }

    /**
     * Hardcoded fallback scoring (original logic).
     * Used when PMML strategies are not available.
     */
    ScoreResult doScore(ScoreInput input) {
        try {
            BigDecimal dirScore = scoreDebtIncomeRatio(input);
            BigDecimal aprScore = scoreWeightedApr(input.weightedApr());
            BigDecimal liqScore = scoreLiquidity(input);
            BigDecimal overdueScore = scoreOverdue(input.overdueCount(), input.maxOverdueDays());
            BigDecimal creditScore = scoreCreditStability(input.debtCount(), input.avgLoanDays());

            BigDecimal finalScore = dirScore.multiply(W_DIR)
                    .add(aprScore.multiply(W_APR))
                    .add(liqScore.multiply(W_LIQ))
                    .add(overdueScore.multiply(W_OVD))
                    .add(creditScore.multiply(W_CST))
                    .setScale(SCORE_SCALE, ROUNDING);

            RiskLevel riskLevel = mapRiskLevel(finalScore);
            Recommendation recommendation = mapRecommendation(finalScore, RESTRUCTURE_THRESHOLD);
            String message = buildMessage(recommendation);
            String nextPage = buildNextPage(recommendation);

            log.debug("Fallback score result: finalScore={}, riskLevel={}, recommendation={}",
                    finalScore, riskLevel, recommendation);

            List<DimensionDetail> dimensions = buildFallbackDimensions(input,
                    dirScore, aprScore, liqScore, overdueScore, creditScore);

            return new ScoreResult(finalScore, riskLevel, recommendation, message, nextPage,
                    dimensions, LocalDateTime.now(),
                    UserSegment.DEFAULT, "硬编码策略", "fallback", List.of());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Scoring engine calculation failed", e);
            throw new BizException(ErrorCode.ENGINE_SCORE_FAILED, "评分模型计算失败: " + e.getMessage());
        }
    }

    // ===================== Dimension Scorers (fallback) =====================

    private BigDecimal scoreDebtIncomeRatio(ScoreInput input) {
        if (input.monthlyIncome() == null || input.monthlyIncome().compareTo(BigDecimal.ZERO) == 0) {
            log.debug("INCOME_MISSING: no monthly income, returning score=20");
            return new BigDecimal("20");
        }
        BigDecimal ratio = input.monthlyPayment()
                .divide(input.monthlyIncome(), 10, ROUNDING);

        for (int i = 0; i < DIR_LEVELS.size(); i++) {
            if (ratio.compareTo(DIR_LEVELS.get(i)) <= 0) return DIR_SCORES.get(i);
        }
        return DIR_SCORES.get(DIR_SCORES.size() - 1);
    }

    private BigDecimal scoreWeightedApr(BigDecimal weightedApr) {
        if (weightedApr == null) return APR_SCORES.get(2); // mid-range default
        for (int i = 0; i < APR_LEVELS.size(); i++) {
            if (weightedApr.compareTo(APR_LEVELS.get(i)) <= 0) return APR_SCORES.get(i);
        }
        return APR_SCORES.get(APR_SCORES.size() - 1);
    }

    private BigDecimal scoreLiquidity(ScoreInput input) {
        if (input.monthlyIncome() == null || input.monthlyIncome().compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("40");
        }
        if (input.monthlyIncome().compareTo(input.monthlyPayment()) > 0) {
            return new BigDecimal("60");
        }
        return new BigDecimal("30");
    }

    private BigDecimal scoreOverdue(int overdueCount, int maxOverdueDays) {
        if (overdueCount == 0) return new BigDecimal("95");
        if (overdueCount == 1 && maxOverdueDays <= OVD_LEVEL1_MAX_DAYS) return new BigDecimal("70");
        if (overdueCount <= 2 && maxOverdueDays <= OVD_LEVEL2_MAX_DAYS) return new BigDecimal("50");
        if (overdueCount <= 3 && maxOverdueDays <= OVD_LEVEL3_MAX_DAYS) return new BigDecimal("30");
        return new BigDecimal("10");
    }

    private BigDecimal scoreCreditStability(int debtCount, long avgLoanDays) {
        if (debtCount <= CST_LOW_DEBT_COUNT && avgLoanDays >= CST_MIN_AVG_LOAN_DAYS) return new BigDecimal("80");
        if (debtCount <= CST_MED_DEBT_COUNT) return new BigDecimal("60");
        if (debtCount <= CST_HIGH_DEBT_COUNT) return new BigDecimal("40");
        return new BigDecimal("20");
    }

    // ===================== Mapping Helpers =====================

    private RiskLevel mapRiskLevel(BigDecimal finalScore) {
        if (finalScore.compareTo(RISK_BOUNDARIES.get(0)) >= 0) return RiskLevel.LOW;
        if (finalScore.compareTo(RISK_BOUNDARIES.get(1)) >= 0) return RiskLevel.MEDIUM;
        if (finalScore.compareTo(RISK_BOUNDARIES.get(2)) >= 0) return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }

    /**
     * Maps final score to recommendation.
     * CRITICAL: score &lt; 60 → CREDIT_BUILDING path.
     * score &lt; 40 → also CREDIT_BUILDING (not a separate enum).
     * This ensures no user sees "申请失败" (F-13).
     */
    private Recommendation mapRecommendation(BigDecimal finalScore, BigDecimal threshold) {
        if (finalScore.compareTo(threshold) >= 0) {
            return Recommendation.RESTRUCTURE_RECOMMENDED;
        }
        if (finalScore.compareTo(new BigDecimal("40")) >= 0) {
            return Recommendation.OPTIMIZE_FIRST;
        }
        return Recommendation.CREDIT_BUILDING;
    }

    private String buildMessage(Recommendation recommendation) {
        return switch (recommendation) {
            case RESTRUCTURE_RECOMMENDED ->
                    "好消息是，你有优化空间。通过调整债务结构，可以有效降低利息支出";
            case OPTIMIZE_FIRST ->
                    "当前更适合优化信用结构。我们为你准备了 30 天改善路径";
            case CREDIT_BUILDING ->
                    "你的财务结构有提升空间。先从小步骤开始，30 天后重新评估会有明显变化";
        };
    }

    private String buildNextPage(Recommendation recommendation) {
        return switch (recommendation) {
            case RESTRUCTURE_RECOMMENDED -> "利率模拟器（Page 6）";
            case OPTIMIZE_FIRST -> "信用修复路线图（替代利率模拟器）";
            case CREDIT_BUILDING -> "30天行动计划";
        };
    }

    private List<DimensionDetail> buildFallbackDimensions(
            ScoreInput input,
            BigDecimal dirScore, BigDecimal aprScore, BigDecimal liqScore,
            BigDecimal overdueScore, BigDecimal creditScore) {

        List<DimensionDetail> dims = new ArrayList<>();
        dims.add(new DimensionDetail("debtIncomeRatio", "负债收入比",
                input.monthlyIncome() != null && input.monthlyIncome().compareTo(BigDecimal.ZERO) > 0
                        ? input.monthlyPayment().divide(input.monthlyIncome(), 4, ROUNDING)
                        : null,
                dirScore, W_DIR,
                dirScore.multiply(W_DIR).setScale(SCORE_SCALE, ROUNDING),
                null, null));
        dims.add(new DimensionDetail("weightedApr", "综合利率",
                input.weightedApr(), aprScore, W_APR,
                aprScore.multiply(W_APR).setScale(SCORE_SCALE, ROUNDING),
                null, null));
        dims.add(new DimensionDetail("liquidity", "资产流动性",
                null, liqScore, W_LIQ,
                liqScore.multiply(W_LIQ).setScale(SCORE_SCALE, ROUNDING),
                null, null));
        dims.add(new DimensionDetail("overdue", "逾期情况",
                new BigDecimal(input.overdueCount()), overdueScore, W_OVD,
                overdueScore.multiply(W_OVD).setScale(SCORE_SCALE, ROUNDING),
                null, null));
        dims.add(new DimensionDetail("creditStability", "信用稳定度",
                new BigDecimal(input.debtCount()), creditScore, W_CST,
                creditScore.multiply(W_CST).setScale(SCORE_SCALE, ROUNDING),
                null, null));
        return dims;
    }

    // ===================== Public DTOs =====================

    /**
     * Input data for scoring.
     *
     * @param monthlyPayment  total monthly payment across all debts
     * @param monthlyIncome   monthly income (may be null if not provided)
     * @param weightedApr     weighted APR across debts (percentage form, e.g. 21.4 means 21.4%)
     * @param overdueCount    number of overdue debts
     * @param maxOverdueDays  maximum overdue days across all debts
     * @param debtCount       total number of debts
     * @param avgLoanDays     average loan duration in days
     */
    public record ScoreInput(
            BigDecimal monthlyPayment,
            BigDecimal monthlyIncome,
            BigDecimal weightedApr,
            int overdueCount,
            int maxOverdueDays,
            int debtCount,
            long avgLoanDays
    ) {}

    /**
     * Full scoring result with dimension breakdown and recommendation.
     */
    public record ScoreResult(
            BigDecimal finalScore,
            RiskLevel riskLevel,
            Recommendation recommendation,
            String message,
            String nextPage,
            List<DimensionDetail> dimensions,
            LocalDateTime calculatedAt,
            UserSegment segment,
            String strategyName,
            String strategyVersion,
            List<String> reasonCodes
    ) {
        /** Backwards-compatible constructor (no PMML fields). */
        public ScoreResult(BigDecimal finalScore, RiskLevel riskLevel,
                           Recommendation recommendation, String message, String nextPage,
                           List<DimensionDetail> dimensions, LocalDateTime calculatedAt) {
            this(finalScore, riskLevel, recommendation, message, nextPage,
                    dimensions, calculatedAt, UserSegment.DEFAULT, null, null, List.of());
        }
    }

    /**
     * Single dimension scoring detail for explainability.
     */
    public record DimensionDetail(
            String name,
            String label,
            BigDecimal inputValue,
            BigDecimal score,
            BigDecimal weight,
            BigDecimal weightedScore,
            String explanation,
            String improvementTip
    ) {
        /** Backwards-compatible constructor (no explanation fields). */
        public DimensionDetail(String name, String label, BigDecimal inputValue,
                               BigDecimal score, BigDecimal weight, BigDecimal weightedScore) {
            this(name, label, inputValue, score, weight, weightedScore, null, null);
        }
    }

    /**
     * Recommendation code based on final score.
     * score >= threshold → RESTRUCTURE_RECOMMENDED
     * score >= 40        → OPTIMIZE_FIRST
     * score < 40         → CREDIT_BUILDING (never shows rejection to user — F-13)
     */
    public enum Recommendation {
        RESTRUCTURE_RECOMMENDED,
        OPTIMIZE_FIRST,
        CREDIT_BUILDING
    }
}
