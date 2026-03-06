package com.youhua.engine;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.engine.apr.AprCalculator;
import com.youhua.engine.apr.AprCalculator.DebtAprEntry;
import com.youhua.engine.apr.AprConfig;
import com.youhua.engine.rules.RuleEngine;
import com.youhua.engine.rules.RuleEngine.DebtRuleEntry;
import com.youhua.engine.rules.RuleEngine.RuleInput;
import com.youhua.engine.rules.RuleEngine.RuleResult;
import com.youhua.engine.scoring.ScoringEngine;
import com.youhua.engine.scoring.ScoringEngine.Recommendation;
import com.youhua.engine.scoring.ScoringEngine.ScoreInput;
import com.youhua.engine.scoring.ScoringEngine.ScoreResult;
import com.youhua.engine.scoring.pmml.PmmlScorecardEvaluator;
import com.youhua.engine.scoring.pmml.PmmlStrategyRegistry;
import com.youhua.engine.scoring.pmml.StrategyMetadataLoader;
import com.youhua.engine.scoring.pmml.UserSegmentMatcher;
import com.youhua.profile.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test verifying three-engine collaboration:
 * AprCalculator + ScoringEngine + RuleEngine
 *
 * Tests 5 user profiles from mock-data.md:
 * - User A: Healthy (LOW risk, RESTRUCTURE_RECOMMENDED)
 * - User B: Attention needed (MEDIUM risk, RESTRUCTURE_RECOMMENDED)
 * - User C: Critical (CRITICAL risk, CREDIT_BUILDING) — score < 60 path
 * - User D: Zero-interest edge case (LOW risk)
 * - User E: No confirmed debts (BLOCK by DATA_001)
 *
 * Engines are hand-built with default weights; no Spring context or database required.
 */
@DisplayName("Engine Integration Tests — Three-Engine Collaboration")
class EngineIntegrationTest {

    private AprCalculator aprCalculator;
    private ScoringEngine scoringEngine;
    private RuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        AprConfig aprConfig = new AprConfig();
        aprConfig.setWarningThreshold(new BigDecimal("36.0"));
        aprConfig.setDangerThreshold(new BigDecimal("100.0"));
        aprConfig.setAbnormalThreshold(new BigDecimal("1000.0"));
        aprConfig.setMaxAllowed(new BigDecimal("10000.0"));
        aprCalculator = new AprCalculator(aprConfig);

        StrategyMetadataLoader loader = new StrategyMetadataLoader();
        PmmlStrategyRegistry registry = new PmmlStrategyRegistry(loader);
        registry.init();
        scoringEngine = new ScoringEngine(registry,
                new PmmlScorecardEvaluator(), new UserSegmentMatcher());

        ruleEngine = new RuleEngine();
        ReflectionTestUtils.setField(ruleEngine, "aprWarningThreshold", new BigDecimal("36.0"));
        ReflectionTestUtils.setField(ruleEngine, "aprMaxAllowed", new BigDecimal("10000.0"));
        ReflectionTestUtils.setField(ruleEngine, "extremeDebtRatio", new BigDecimal("0.9"));
        ReflectionTestUtils.setField(ruleEngine, "highTotalDebt", new BigDecimal("10000000"));
        ReflectionTestUtils.setField(ruleEngine, "ocrLowConfidence", new BigDecimal("70"));
    }

    // User A — Healthy profile
    @Test
    @DisplayName("should_pass_rules_and_score_LOW_risk_and_recommend_restructure_when_user_A_healthy_profile")
    void should_pass_rules_and_score_LOW_risk_and_recommend_restructure_when_user_A_healthy_profile() {
        BigDecimal apr1 = aprCalculator.calculateApr(
                new BigDecimal("30000.00"), new BigDecimal("31500.00"), 365);
        BigDecimal apr2 = aprCalculator.calculateApr(
                new BigDecimal("8000.00"), new BigDecimal("8800.00"), 180);
        BigDecimal apr3 = aprCalculator.calculateApr(
                new BigDecimal("12000.00"), new BigDecimal("13200.00"), 365);

        assertThat(apr1).isGreaterThan(BigDecimal.ZERO);
        assertThat(apr2).isGreaterThan(BigDecimal.ZERO);
        assertThat(apr3).isGreaterThan(BigDecimal.ZERO);

        BigDecimal weightedApr = aprCalculator.calculateWeightedApr(List.of(
                new DebtAprEntry(new BigDecimal("30000.00"), apr1),
                new DebtAprEntry(new BigDecimal("8000.00"), apr2),
                new DebtAprEntry(new BigDecimal("12000.00"), apr3)
        ));
        assertThat(weightedApr).isGreaterThan(BigDecimal.ZERO).isLessThan(new BigDecimal("15.0"));

        BigDecimal monthlyIncome = new BigDecimal("15000.00");
        BigDecimal monthlyPayment = new BigDecimal("3700.00");

        RuleResult ruleResult = ruleEngine.evaluate(new RuleInput(
                3,
                List.of(
                        new DebtRuleEntry("招商银行信用卡", new BigDecimal("30000.00"),
                                new BigDecimal("31500.00"), 365, OverdueStatus.NORMAL,
                                DebtSourceType.MANUAL, null, apr1),
                        new DebtRuleEntry("花呗", new BigDecimal("8000.00"),
                                new BigDecimal("8800.00"), 180, OverdueStatus.NORMAL,
                                DebtSourceType.MANUAL, null, apr2),
                        new DebtRuleEntry("京东白条", new BigDecimal("12000.00"),
                                new BigDecimal("13200.00"), 365, OverdueStatus.NORMAL,
                                DebtSourceType.OCR, new BigDecimal("88.5"), apr3)
                ),
                new BigDecimal("50000.00"), monthlyPayment, monthlyIncome
        ));

        assertThat(ruleResult.canProceed()).isTrue();
        assertThat(ruleResult.blocked()).isFalse();
        assertThat(ruleResult.warnings()).doesNotContain("URGENT_OVERDUE");

        ScoreResult scoreResult = scoringEngine.score(new ScoreInput(
                monthlyPayment, monthlyIncome, weightedApr, 0, 0, 3, 303L
        ));

        assertThat(scoreResult.finalScore()).isGreaterThanOrEqualTo(new BigDecimal("80"));
        assertThat(scoreResult.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(scoreResult.recommendation()).isEqualTo(Recommendation.RESTRUCTURE_RECOMMENDED);
        assertThat(scoreResult.message()).startsWith("好消息是");
        assertThat(scoreResult.dimensions()).hasSize(5);
    }

    // User B — Attention-needed profile
    @Test
    @DisplayName("should_pass_rules_without_urgent_warn_and_score_at_least_60_when_user_B_attention_profile")
    void should_pass_rules_without_urgent_warn_and_score_at_least_60_when_user_B_attention_profile() {
        BigDecimal apr1 = aprCalculator.calculateApr(
                new BigDecimal("200000.00"), new BigDecimal("240000.00"), 730);
        BigDecimal apr2 = aprCalculator.calculateApr(
                new BigDecimal("50000.00"), new BigDecimal("62000.00"), 365);
        BigDecimal apr3 = aprCalculator.calculateApr(
                new BigDecimal("30000.00"), new BigDecimal("39000.00"), 365);

        BigDecimal weightedApr = aprCalculator.calculateWeightedApr(List.of(
                new DebtAprEntry(new BigDecimal("200000.00"), apr1),
                new DebtAprEntry(new BigDecimal("50000.00"), apr2),
                new DebtAprEntry(new BigDecimal("30000.00"), apr3)
        ));
        assertThat(weightedApr).isBetween(new BigDecimal("14.0"), new BigDecimal("25.0"));

        BigDecimal monthlyIncome = new BigDecimal("23000.00");
        BigDecimal monthlyPayment = new BigDecimal("12200.00");

        RuleResult ruleResult = ruleEngine.evaluate(new RuleInput(
                3,
                List.of(
                        new DebtRuleEntry("工商银行经营贷", new BigDecimal("200000.00"),
                                new BigDecimal("240000.00"), 730, OverdueStatus.NORMAL,
                                DebtSourceType.MANUAL, null, apr1),
                        new DebtRuleEntry("微众银行", new BigDecimal("50000.00"),
                                new BigDecimal("62000.00"), 365, OverdueStatus.NORMAL,
                                DebtSourceType.OCR, new BigDecimal("75.0"), apr2),
                        new DebtRuleEntry("平安普惠", new BigDecimal("30000.00"),
                                new BigDecimal("39000.00"), 365, OverdueStatus.OVERDUE_30,
                                DebtSourceType.MANUAL, null, apr3)
                ),
                new BigDecimal("280000.00"), monthlyPayment, monthlyIncome
        ));

        assertThat(ruleResult.canProceed()).isTrue();
        assertThat(ruleResult.blocked()).isFalse();
        // OVERDUE_30 does NOT trigger URGENT_OVERDUE (only OVERDUE_60 / OVERDUE_90_PLUS)
        assertThat(ruleResult.warnings()).doesNotContain("URGENT_OVERDUE");

        ScoreResult scoreResult = scoringEngine.score(new ScoreInput(
                monthlyPayment, monthlyIncome, weightedApr, 1, 15, 3, 486L
        ));

        assertThat(scoreResult.finalScore()).isGreaterThanOrEqualTo(new BigDecimal("60"));
        assertThat(scoreResult.riskLevel()).isIn(RiskLevel.MEDIUM, RiskLevel.LOW);
        assertThat(scoreResult.recommendation()).isEqualTo(Recommendation.RESTRUCTURE_RECOMMENDED);
        assertThat(scoreResult.message()).startsWith("好消息是");
    }

    // User C — Critical profile: score < 60 MUST yield CREDIT_BUILDING (F-13)
    @Test
    @DisplayName("should_trigger_urgent_overdue_warn_and_score_below_60_and_recommend_CREDIT_BUILDING_when_user_C_critical_profile")
    void should_trigger_urgent_overdue_warn_and_score_below_60_and_recommend_CREDIT_BUILDING_when_user_C_critical_profile() {
        BigDecimal apr1 = aprCalculator.calculateApr(
                new BigDecimal("50000.00"), new BigDecimal("72000.00"), 365);
        BigDecimal apr2 = aprCalculator.calculateApr(
                new BigDecimal("80000.00"), new BigDecimal("120000.00"), 365);
        BigDecimal apr3 = aprCalculator.calculateApr(
                new BigDecimal("60000.00"), new BigDecimal("78000.00"), 365);
        BigDecimal apr4 = aprCalculator.calculateApr(
                new BigDecimal("40000.00"), new BigDecimal("52000.00"), 365);
        BigDecimal apr5 = aprCalculator.calculateApr(
                new BigDecimal("150000.00"), new BigDecimal("195000.00"), 365);
        BigDecimal apr6 = aprCalculator.calculateApr(
                new BigDecimal("100000.00"), new BigDecimal("100000.00"), 365);
        BigDecimal apr7 = aprCalculator.calculateApr(
                new BigDecimal("20000.00"), new BigDecimal("28000.00"), 180);

        BigDecimal weightedApr = aprCalculator.calculateWeightedApr(List.of(
                new DebtAprEntry(new BigDecimal("50000.00"), apr1),
                new DebtAprEntry(new BigDecimal("80000.00"), apr2),
                new DebtAprEntry(new BigDecimal("60000.00"), apr3),
                new DebtAprEntry(new BigDecimal("40000.00"), apr4),
                new DebtAprEntry(new BigDecimal("150000.00"), apr5),
                new DebtAprEntry(new BigDecimal("100000.00"), apr6),
                new DebtAprEntry(new BigDecimal("20000.00"), apr7)
        ));
        assertThat(weightedApr).isBetween(new BigDecimal("20.0"), new BigDecimal("40.0"));

        BigDecimal monthlyIncome = new BigDecimal("12000.00");
        BigDecimal monthlyPayment = new BigDecimal("18500.00");

        RuleResult ruleResult = ruleEngine.evaluate(new RuleInput(
                7,
                List.of(
                        new DebtRuleEntry("某网贷平台A", new BigDecimal("50000.00"),
                                new BigDecimal("72000.00"), 365, OverdueStatus.OVERDUE_60,
                                DebtSourceType.MANUAL, null, apr1),
                        new DebtRuleEntry("某网贷平台B", new BigDecimal("80000.00"),
                                new BigDecimal("120000.00"), 365, OverdueStatus.OVERDUE_90_PLUS,
                                DebtSourceType.MANUAL, null, apr2),
                        new DebtRuleEntry("信用卡A", new BigDecimal("60000.00"),
                                new BigDecimal("78000.00"), 365, OverdueStatus.OVERDUE_30,
                                DebtSourceType.MANUAL, null, apr3),
                        new DebtRuleEntry("信用卡B", new BigDecimal("40000.00"),
                                new BigDecimal("52000.00"), 365, OverdueStatus.NORMAL,
                                DebtSourceType.OCR, new BigDecimal("62.0"), apr4),
                        new DebtRuleEntry("经营贷", new BigDecimal("150000.00"),
                                new BigDecimal("195000.00"), 365, OverdueStatus.NORMAL,
                                DebtSourceType.MANUAL, null, apr5),
                        new DebtRuleEntry("亲友借款", new BigDecimal("100000.00"),
                                new BigDecimal("100000.00"), 365, OverdueStatus.NORMAL,
                                DebtSourceType.MANUAL, null, apr6),
                        new DebtRuleEntry("某分期平台", new BigDecimal("20000.00"),
                                new BigDecimal("28000.00"), 180, OverdueStatus.NORMAL,
                                DebtSourceType.MANUAL, null, apr7)
                ),
                new BigDecimal("500000.00"), monthlyPayment, monthlyIncome
        ));

        assertThat(ruleResult.canProceed()).isTrue();
        assertThat(ruleResult.blocked()).isFalse();
        assertThat(ruleResult.warnings()).contains("URGENT_OVERDUE");
        assertThat(ruleResult.warnings()).contains("PAYMENT_EXCEED_INCOME");
        assertThat(ruleResult.warnings()).contains("EXTREME_DEBT_RATIO");
        assertThat(ruleResult.warnings()).contains("LOW_CONFIDENCE");

        ScoreResult scoreResult = scoringEngine.score(new ScoreInput(
                monthlyPayment, monthlyIncome, weightedApr, 3, 120, 7, 339L
        ));

        // F-13 critical: score < 60 → CREDIT_BUILDING, never rejection message
        assertThat(scoreResult.finalScore()).isLessThan(new BigDecimal("60"));
        assertThat(scoreResult.recommendation()).isEqualTo(Recommendation.CREDIT_BUILDING);
        assertThat(scoreResult.message()).doesNotContain("申请失败");
        assertThat(scoreResult.message()).doesNotContain("不符合条件");
        assertThat(scoreResult.message()).contains("提升空间");
        assertThat(scoreResult.riskLevel()).isIn(RiskLevel.HIGH, RiskLevel.CRITICAL);
    }

    // User D — Zero-interest edge case
    @Test
    @DisplayName("should_return_zero_apr_and_score_LOW_risk_when_user_D_zero_interest_debt")
    void should_return_zero_apr_and_score_LOW_risk_when_user_D_zero_interest_debt() {
        BigDecimal apr = aprCalculator.calculateApr(
                new BigDecimal("6000.00"), new BigDecimal("6000.00"), 90);
        assertThat(apr).isEqualByComparingTo(BigDecimal.ZERO);

        BigDecimal weightedApr = aprCalculator.calculateWeightedApr(List.of(
                new DebtAprEntry(new BigDecimal("6000.00"), apr)
        ));
        assertThat(weightedApr).isEqualByComparingTo(BigDecimal.ZERO);

        BigDecimal monthlyIncome = new BigDecimal("8000.00");
        BigDecimal monthlyPayment = new BigDecimal("2000.00");

        RuleResult ruleResult = ruleEngine.evaluate(new RuleInput(
                1,
                List.of(new DebtRuleEntry("花呗免息分期", new BigDecimal("6000.00"),
                        new BigDecimal("6000.00"), 90, OverdueStatus.NORMAL,
                        DebtSourceType.MANUAL, null, apr)),
                new BigDecimal("6000.00"), monthlyPayment, monthlyIncome
        ));

        assertThat(ruleResult.canProceed()).isTrue();
        assertThat(ruleResult.blocked()).isFalse();
        assertThat(ruleResult.warnings()).isEmpty();

        ScoreResult scoreResult = scoringEngine.score(new ScoreInput(
                monthlyPayment, monthlyIncome, weightedApr, 0, 0, 1, 90L
        ));

        assertThat(scoreResult.finalScore()).isGreaterThanOrEqualTo(new BigDecimal("80"));
        assertThat(scoreResult.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(scoreResult.recommendation()).isEqualTo(Recommendation.RESTRUCTURE_RECOMMENDED);
    }

    // User E — No confirmed debts (boundary case)
    @Test
    @DisplayName("should_block_with_DATA_001_error_and_throw_BizException_when_user_E_no_confirmed_debts")
    void should_block_with_DATA_001_error_and_throw_BizException_when_user_E_no_confirmed_debts() {
        assertThatThrownBy(() -> ruleEngine.evaluate(new RuleInput(
                0, List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        )))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_RULE_FAILED))
                .hasMessageContaining("暂无已确认的债务数据");
    }

    // Cross-cutting: F-13 guard — score < 60 always yields CREDIT_BUILDING
    @Test
    @DisplayName("should_always_recommend_CREDIT_BUILDING_and_never_show_rejection_message_when_score_below_60")
    void should_always_recommend_CREDIT_BUILDING_and_never_show_rejection_message_when_score_below_60() {
        ScoreResult scoreResult = scoringEngine.score(new ScoreInput(
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                new BigDecimal("50.0"),
                5, 180,
                9, 60L
        ));

        assertThat(scoreResult.finalScore()).isLessThan(new BigDecimal("60"));
        assertThat(scoreResult.recommendation()).isEqualTo(Recommendation.CREDIT_BUILDING);
        assertThat(scoreResult.message()).doesNotContain("申请失败");
        assertThat(scoreResult.message()).doesNotContain("不符合条件");
        assertThat(scoreResult.message()).doesNotContain("问题严重");
        assertThat(scoreResult.message()).doesNotContain("赶紧行动");
        assertThat(scoreResult.message()).isNotBlank();
    }

    // Full pipeline: deterministic three-engine result
    @Test
    @DisplayName("should_complete_full_pipeline_with_deterministic_results_when_combining_all_three_engines")
    void should_complete_full_pipeline_with_deterministic_results_when_combining_all_three_engines() {
        BigDecimal principal1 = new BigDecimal("100000.00");
        BigDecimal repayment1 = new BigDecimal("120000.00");
        BigDecimal principal2 = new BigDecimal("50000.00");
        BigDecimal repayment2 = new BigDecimal("68000.00");

        BigDecimal apr1 = aprCalculator.calculateApr(principal1, repayment1, 365); // 20%
        BigDecimal apr2 = aprCalculator.calculateApr(principal2, repayment2, 365); // 36%

        assertThat(apr1).isEqualByComparingTo(new BigDecimal("20.000000"));
        assertThat(apr2).isEqualByComparingTo(new BigDecimal("36.000000"));

        BigDecimal weightedApr = aprCalculator.calculateWeightedApr(List.of(
                new DebtAprEntry(principal1, apr1),
                new DebtAprEntry(principal2, apr2)
        ));
        assertThat(weightedApr).isEqualByComparingTo(new BigDecimal("25.333333"));

        BigDecimal monthlyIncome = new BigDecimal("18000.00");
        BigDecimal monthlyPayment = new BigDecimal("8000.00");

        RuleResult ruleResult = ruleEngine.evaluate(new RuleInput(
                2,
                List.of(
                        new DebtRuleEntry("银行A", principal1, repayment1, 365,
                                OverdueStatus.NORMAL, DebtSourceType.MANUAL, null, apr1),
                        new DebtRuleEntry("平台B", principal2, repayment2, 365,
                                OverdueStatus.NORMAL, DebtSourceType.MANUAL, null, apr2)
                ),
                new BigDecimal("150000.00"), monthlyPayment, monthlyIncome
        ));

        assertThat(ruleResult.canProceed()).isTrue();
        assertThat(ruleResult.blocked()).isFalse();

        // finalScore = 70*0.30 + 35*0.25 + 60*0.15 + 95*0.20 + 80*0.10 = 65.75
        ScoreResult scoreResult = scoringEngine.score(new ScoreInput(
                monthlyPayment, monthlyIncome, weightedApr, 0, 0, 2, 365L
        ));

        assertThat(scoreResult.finalScore()).isEqualByComparingTo(new BigDecimal("65.75"));
        assertThat(scoreResult.recommendation()).isEqualTo(Recommendation.RESTRUCTURE_RECOMMENDED);
    }

    // Boundary: empty weighted APR list returns 0
    @Test
    @DisplayName("should_return_zero_weighted_apr_without_exception_when_debt_list_is_empty")
    void should_return_zero_weighted_apr_without_exception_when_debt_list_is_empty() {
        BigDecimal result = aprCalculator.calculateWeightedApr(List.of());
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
