package com.youhua.engine.scoring.pmml;

import com.youhua.engine.scoring.ScoringEngine.DimensionDetail;
import com.youhua.engine.scoring.ScoringEngine.ScoreInput;
import com.youhua.engine.scoring.pmml.PmmlScorecardEvaluator.PmmlEvalResult;
import com.youhua.profile.enums.RiskLevel;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PmmlScorecardEvaluator Tests")
class PmmlScorecardEvaluatorTest {

    private static Evaluator evaluator;
    private static PmmlScorecardEvaluator pmmlEvaluator;
    private static StrategyMetadata metadata;

    @BeforeAll
    static void setUp() throws Exception {
        try (InputStream is = PmmlScorecardEvaluatorTest.class.getClassLoader()
                .getResourceAsStream("strategies/default.pmml")) {
            evaluator = new LoadingModelEvaluatorBuilder().load(is).build();
            evaluator.verify();
        }

        pmmlEvaluator = new PmmlScorecardEvaluator();
        metadata = new StrategyMetadataLoader().loadFromClasspath("strategies/default.meta.yml");
    }

    @Test
    @DisplayName("should_evaluate_excellent_profile_matching_java_logic")
    void should_evaluate_excellent_profile_matching_java_logic() {
        // ratio=0.25 → DIR: 27, apr=12 → APR: 18.75, income>payment → LIQ: 9
        // overdue=0 → OVD: 19, debtCount=2 avgLoanDays=200 → CST: 8
        // total = 81.75
        ScoreInput input = new ScoreInput(
                new BigDecimal("5000"), new BigDecimal("20000"),
                new BigDecimal("12.0"), 0, 0, 2, 200L);

        PmmlEvalResult result = pmmlEvaluator.evaluate(evaluator, input);

        assertThat(result.finalScore()).isEqualByComparingTo(new BigDecimal("81.75"));
    }

    @Test
    @DisplayName("should_evaluate_medium_profile")
    void should_evaluate_medium_profile() {
        // ratio=0.62 → DIR: 15, apr=21.4 → APR: 13.75, income>payment → LIQ: 9
        // overdue=1,30d → OVD: 14, debtCount=4 → CST: 6
        // total = 57.75
        ScoreInput input = new ScoreInput(
                new BigDecimal("6200"), new BigDecimal("10000"),
                new BigDecimal("21.4"), 1, 30, 4, 90L);

        PmmlEvalResult result = pmmlEvaluator.evaluate(evaluator, input);

        assertThat(result.finalScore()).isEqualByComparingTo(new BigDecimal("57.75"));
    }

    @Test
    @DisplayName("should_evaluate_worst_profile")
    void should_evaluate_worst_profile() {
        // ratio=0.95 → DIR: 3, apr=45 → APR: 3.75, income>payment → LIQ: 9
        // overdue=4,120d → OVD: 2, debtCount=8 → CST: 2
        // total = 19.75
        ScoreInput input = new ScoreInput(
                new BigDecimal("9500"), new BigDecimal("10000"),
                new BigDecimal("45.0"), 4, 120, 8, 60L);

        PmmlEvalResult result = pmmlEvaluator.evaluate(evaluator, input);

        assertThat(result.finalScore()).isEqualByComparingTo(new BigDecimal("19.75"));
    }

    @Test
    @DisplayName("should_handle_null_income")
    void should_handle_null_income() {
        // debtIncomeRatio=missing → DIR: 6, apr=15 → APR: 18.75
        // monthlyIncome=missing → LIQ: 6, overdue=0 → OVD: 19
        // debtCount=1, avgLoanDays=365 → CST: 8
        // total = 57.75
        ScoreInput input = new ScoreInput(
                new BigDecimal("5000"), null,
                new BigDecimal("15.0"), 0, 0, 1, 365L);

        PmmlEvalResult result = pmmlEvaluator.evaluate(evaluator, input);

        assertThat(result.finalScore()).isEqualByComparingTo(new BigDecimal("57.75"));
    }

    @Test
    @DisplayName("should_handle_zero_income")
    void should_handle_zero_income() {
        // debtIncomeRatio=missing → DIR: 6, apr=15 → APR: 18.75
        // monthlyIncome=0 → LIQ: 6, overdue=0 → OVD: 19
        // debtCount=1, avgLoanDays=365 → CST: 8
        // total = 57.75
        ScoreInput input = new ScoreInput(
                new BigDecimal("5000"), BigDecimal.ZERO,
                new BigDecimal("15.0"), 0, 0, 1, 365L);

        PmmlEvalResult result = pmmlEvaluator.evaluate(evaluator, input);

        assertThat(result.finalScore()).isEqualByComparingTo(new BigDecimal("57.75"));
    }

    @Test
    @DisplayName("should_return_reason_codes")
    void should_return_reason_codes() {
        ScoreInput input = new ScoreInput(
                new BigDecimal("8500"), new BigDecimal("10000"),
                new BigDecimal("38.0"), 2, 45, 5, 120L);

        PmmlEvalResult result = pmmlEvaluator.evaluate(evaluator, input);

        assertThat(result.reasonCodes()).isNotEmpty();
        // All codes should be from our set
        assertThat(result.reasonCodes()).allSatisfy(
                code -> assertThat(code).isIn("DIR", "APR", "LIQ", "OVD", "CST"));
    }

    @Test
    @DisplayName("should_build_dimensions_with_explanation")
    void should_build_dimensions_with_explanation() {
        ScoreInput input = new ScoreInput(
                new BigDecimal("5000"), new BigDecimal("20000"),
                new BigDecimal("12.0"), 0, 0, 2, 200L);

        PmmlEvalResult result = pmmlEvaluator.evaluate(evaluator, input);
        List<DimensionDetail> dims = pmmlEvaluator.buildDimensions(result, input, metadata);

        assertThat(dims).hasSize(5);
        assertThat(dims).extracting(DimensionDetail::name)
                .containsExactlyInAnyOrder(
                        "debtIncomeRatio", "weightedApr", "liquidity", "overdue", "creditStability");

        // Check that explanation and improvementTip are populated
        DimensionDetail dirDim = dims.stream()
                .filter(d -> "debtIncomeRatio".equals(d.name())).findFirst().orElseThrow();
        assertThat(dirDim.explanation()).isNotNull();
        assertThat(dirDim.improvementTip()).isNotNull();
    }

    @Test
    @DisplayName("should_map_risk_level_correctly")
    void should_map_risk_level_correctly() {
        assertThat(pmmlEvaluator.mapRiskLevel(new BigDecimal("85"), metadata)).isEqualTo(RiskLevel.LOW);
        assertThat(pmmlEvaluator.mapRiskLevel(new BigDecimal("65"), metadata)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(pmmlEvaluator.mapRiskLevel(new BigDecimal("45"), metadata)).isEqualTo(RiskLevel.HIGH);
        assertThat(pmmlEvaluator.mapRiskLevel(new BigDecimal("35"), metadata)).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    @DisplayName("should_handle_null_apr")
    void should_handle_null_apr() {
        // apr=null → APR: 13.75 (mid-range default, same as Java)
        ScoreInput input = new ScoreInput(
                new BigDecimal("3000"), new BigDecimal("10000"),
                null, 0, 0, 2, 200L);

        PmmlEvalResult result = pmmlEvaluator.evaluate(evaluator, input);

        // ratio=0.3 → DIR:27, apr=null → APR:13.75, income>payment → LIQ:9
        // overdue=0 → OVD:19, debtCount=2 avgLoanDays=200 → CST:8
        // total = 76.75
        assertThat(result.finalScore()).isEqualByComparingTo(new BigDecimal("76.75"));
    }

    @Test
    @DisplayName("should_match_boundary_debt_ratio_0_30")
    void should_match_boundary_debt_ratio_0_30() {
        // ratio = 3000/10000 = 0.30 → lessOrEqual 0.30 → DIR: 27
        ScoreInput input = new ScoreInput(
                new BigDecimal("3000"), new BigDecimal("10000"),
                new BigDecimal("15.0"), 0, 0, 2, 200L);

        PmmlEvalResult result = pmmlEvaluator.evaluate(evaluator, input);

        // DIR:27, APR:18.75, LIQ:9, OVD:19, CST:8 = 81.75
        assertThat(result.finalScore()).isEqualByComparingTo(new BigDecimal("81.75"));
    }
}
