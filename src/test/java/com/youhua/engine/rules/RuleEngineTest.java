package com.youhua.engine.rules;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.engine.rules.RuleEngine.DebtRuleEntry;
import com.youhua.engine.rules.RuleEngine.RuleInput;
import com.youhua.engine.rules.RuleEngine.RuleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RuleEngine.
 * Coverage: DATA rules (BLOCK) + VALUE rules (BLOCK/WARN) + BIZ rules (WARN) + normal pass path.
 */
@DisplayName("RuleEngine Tests")
class RuleEngineTest {

    private RuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new RuleEngine();
        ReflectionTestUtils.setField(ruleEngine, "aprWarningThreshold", new BigDecimal("36.0"));
        ReflectionTestUtils.setField(ruleEngine, "aprMaxAllowed", new BigDecimal("10000.0"));
        ReflectionTestUtils.setField(ruleEngine, "extremeDebtRatio", new BigDecimal("0.9"));
        ReflectionTestUtils.setField(ruleEngine, "highTotalDebt", new BigDecimal("10000000"));
        ReflectionTestUtils.setField(ruleEngine, "ocrLowConfidence", new BigDecimal("70"));
    }

    /** Creates a valid debt entry for reuse in tests */
    private DebtRuleEntry validDebt(String creditor) {
        return new DebtRuleEntry(
                creditor,
                new BigDecimal("10000"),
                new BigDecimal("12000"),
                365,
                OverdueStatus.NORMAL,
                DebtSourceType.MANUAL,
                null,
                new BigDecimal("20.0")
        );
    }

    // ===== DATA_001: At least one confirmed debt =====

    @Test
    @DisplayName("DATA_001: should_throw_BizException_ENGINE_RULE_FAILED_when_no_confirmed_debts")
    void should_throw_BizException_ENGINE_RULE_FAILED_when_no_confirmed_debts() {
        RuleInput input = new RuleInput(
                0,
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("10000")
        );

        assertThatThrownBy(() -> ruleEngine.evaluate(input))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_RULE_FAILED))
                .hasMessageContaining("暂无已确认的债务数据");
    }

    // ===== DATA_002: Principal > 0 =====

    @Test
    @DisplayName("DATA_002: should_throw_BizException_ENGINE_RULE_FAILED_when_principal_is_zero")
    void should_throw_BizException_ENGINE_RULE_FAILED_when_principal_is_zero() {
        DebtRuleEntry invalidDebt = new DebtRuleEntry(
                "TestBank",
                BigDecimal.ZERO,
                new BigDecimal("1000"),
                365,
                OverdueStatus.NORMAL,
                DebtSourceType.MANUAL,
                null,
                new BigDecimal("20.0")
        );
        RuleInput input = new RuleInput(1, List.of(invalidDebt),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10000"));

        assertThatThrownBy(() -> ruleEngine.evaluate(input))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_RULE_FAILED))
                .hasMessageContaining("本金为零或负数");
    }

    @Test
    @DisplayName("DATA_002: should_throw_BizException_ENGINE_RULE_FAILED_when_principal_is_negative")
    void should_throw_BizException_ENGINE_RULE_FAILED_when_principal_is_negative() {
        DebtRuleEntry invalidDebt = new DebtRuleEntry(
                "TestBank",
                new BigDecimal("-1000"),
                new BigDecimal("1000"),
                365,
                OverdueStatus.NORMAL,
                DebtSourceType.MANUAL,
                null,
                new BigDecimal("20.0")
        );
        RuleInput input = new RuleInput(1, List.of(invalidDebt),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10000"));

        assertThatThrownBy(() -> ruleEngine.evaluate(input))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("本金为零或负数");
    }

    // ===== DATA_003: loanDays > 0 =====

    @Test
    @DisplayName("DATA_003: should_throw_BizException_ENGINE_RULE_FAILED_when_loan_days_is_zero")
    void should_throw_BizException_ENGINE_RULE_FAILED_when_loan_days_is_zero() {
        DebtRuleEntry invalidDebt = new DebtRuleEntry(
                "TestBank",
                new BigDecimal("10000"),
                new BigDecimal("11000"),
                0,
                OverdueStatus.NORMAL,
                DebtSourceType.MANUAL,
                null,
                new BigDecimal("20.0")
        );
        RuleInput input = new RuleInput(1, List.of(invalidDebt),
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));

        assertThatThrownBy(() -> ruleEngine.evaluate(input))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("借款天数为零或负数");
    }

    // ===== DATA_004: totalRepayment >= principal =====

    @Test
    @DisplayName("DATA_004: should_throw_BizException_ENGINE_RULE_FAILED_when_repayment_less_than_principal")
    void should_throw_BizException_ENGINE_RULE_FAILED_when_repayment_less_than_principal() {
        DebtRuleEntry invalidDebt = new DebtRuleEntry(
                "TestBank",
                new BigDecimal("10000"),
                new BigDecimal("9000"),
                365,
                OverdueStatus.NORMAL,
                DebtSourceType.MANUAL,
                null,
                null
        );
        RuleInput input = new RuleInput(1, List.of(invalidDebt),
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));

        assertThatThrownBy(() -> ruleEngine.evaluate(input))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("总还款额小于本金");
    }

    // ===== VALUE_001: APR max check =====

    @Test
    @DisplayName("VALUE_001: should_throw_BizException_ENGINE_RULE_FAILED_when_apr_exceeds_max")
    void should_throw_BizException_ENGINE_RULE_FAILED_when_apr_exceeds_max() {
        DebtRuleEntry debtWithHighApr = new DebtRuleEntry(
                "UsurBank",
                new BigDecimal("10000"),
                new BigDecimal("20000"),
                365,
                OverdueStatus.NORMAL,
                DebtSourceType.MANUAL,
                null,
                new BigDecimal("10001.0")
        );
        RuleInput input = new RuleInput(1, List.of(debtWithHighApr),
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));

        assertThatThrownBy(() -> ruleEngine.evaluate(input))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("APR 计算结果异常");
    }

    // ===== VALUE_002: APR high-interest warning =====

    @Test
    @DisplayName("VALUE_002: should_add_HIGH_INTEREST_warning_when_apr_exceeds_threshold")
    void should_add_HIGH_INTEREST_warning_when_apr_exceeds_threshold() {
        DebtRuleEntry highInterestDebt = new DebtRuleEntry(
                "HighRate Finance",
                new BigDecimal("10000"),
                new BigDecimal("16000"),
                365,
                OverdueStatus.NORMAL,
                DebtSourceType.MANUAL,
                null,
                new BigDecimal("42.5")
        );
        RuleInput input = new RuleInput(1, List.of(highInterestDebt),
                new BigDecimal("10000"), new BigDecimal("1500"), new BigDecimal("10000"));

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.canProceed()).isTrue();
        assertThat(result.blocked()).isFalse();
        assertThat(result.warnings()).contains("HIGH_INTEREST");
    }

    @Test
    @DisplayName("VALUE_002: should_not_add_HIGH_INTEREST_warning_when_apr_equals_threshold")
    void should_not_add_HIGH_INTEREST_warning_when_apr_equals_threshold() {
        DebtRuleEntry debtAtThreshold = new DebtRuleEntry(
                "BoundaryBank",
                new BigDecimal("10000"),
                new BigDecimal("13600"),
                365,
                OverdueStatus.NORMAL,
                DebtSourceType.MANUAL,
                null,
                new BigDecimal("36.0")
        );
        RuleInput input = new RuleInput(1, List.of(debtAtThreshold),
                new BigDecimal("10000"), new BigDecimal("1500"), new BigDecimal("10000"));

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.warnings()).doesNotContain("HIGH_INTEREST");
    }

    // ===== VALUE_003: Monthly payment exceeds income =====

    @Test
    @DisplayName("VALUE_003: should_add_PAYMENT_EXCEED_INCOME_warning_when_payment_exceeds_income")
    void should_add_PAYMENT_EXCEED_INCOME_warning_when_payment_exceeds_income() {
        RuleInput input = new RuleInput(1, List.of(validDebt("BankA")),
                new BigDecimal("10000"),
                new BigDecimal("15000"),
                new BigDecimal("10000")
        );

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.canProceed()).isTrue();
        assertThat(result.warnings()).contains("PAYMENT_EXCEED_INCOME");
    }

    @Test
    @DisplayName("VALUE_003: should_not_add_warning_when_income_is_null")
    void should_not_add_warning_when_income_is_null() {
        RuleInput input = new RuleInput(1, List.of(validDebt("BankA")),
                new BigDecimal("10000"),
                new BigDecimal("15000"),
                null
        );

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.warnings()).doesNotContain("PAYMENT_EXCEED_INCOME");
    }

    // ===== VALUE_004: Extreme debt ratio =====

    @Test
    @DisplayName("VALUE_004: should_add_EXTREME_DEBT_RATIO_warning_when_ratio_exceeds_90_percent")
    void should_add_EXTREME_DEBT_RATIO_warning_when_ratio_exceeds_90_percent() {
        // payment=9500, income=10000 → ratio=0.95 > 0.9
        RuleInput input = new RuleInput(1, List.of(validDebt("BankA")),
                new BigDecimal("10000"),
                new BigDecimal("9500"),
                new BigDecimal("10000")
        );

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.warnings()).contains("EXTREME_DEBT_RATIO");
    }

    // ===== VALUE_005: High total debt warning =====

    @Test
    @DisplayName("VALUE_005: should_add_HIGH_TOTAL_DEBT_warning_when_total_debt_exceeds_10_million")
    void should_add_HIGH_TOTAL_DEBT_warning_when_total_debt_exceeds_10_million() {
        RuleInput input = new RuleInput(1, List.of(validDebt("BigLoanBank")),
                new BigDecimal("10000001"),
                new BigDecimal("1000"),
                new BigDecimal("50000")
        );

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.warnings()).contains("HIGH_TOTAL_DEBT");
    }

    // ===== BIZ_003: Urgent overdue =====

    @Test
    @DisplayName("BIZ_003: should_add_URGENT_OVERDUE_warning_when_debt_overdue_60_days")
    void should_add_URGENT_OVERDUE_warning_when_debt_overdue_60_days() {
        DebtRuleEntry overdueDebt = new DebtRuleEntry(
                "OverdueBank",
                new BigDecimal("10000"),
                new BigDecimal("12000"),
                365,
                OverdueStatus.OVERDUE_60,
                DebtSourceType.MANUAL,
                null,
                new BigDecimal("20.0")
        );
        RuleInput input = new RuleInput(1, List.of(overdueDebt),
                new BigDecimal("10000"), new BigDecimal("1500"), new BigDecimal("10000"));

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.warnings()).contains("URGENT_OVERDUE");
    }

    @Test
    @DisplayName("BIZ_003: should_add_URGENT_OVERDUE_warning_when_debt_overdue_90_plus_days")
    void should_add_URGENT_OVERDUE_warning_when_debt_overdue_90_plus_days() {
        DebtRuleEntry overdueDebt = new DebtRuleEntry(
                "LongOverdueBank",
                new BigDecimal("10000"),
                new BigDecimal("12000"),
                365,
                OverdueStatus.OVERDUE_90_PLUS,
                DebtSourceType.MANUAL,
                null,
                new BigDecimal("20.0")
        );
        RuleInput input = new RuleInput(1, List.of(overdueDebt),
                new BigDecimal("10000"), new BigDecimal("1500"), new BigDecimal("10000"));

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.warnings()).contains("URGENT_OVERDUE");
    }

    @Test
    @DisplayName("BIZ_003: should_not_add_URGENT_OVERDUE_warning_when_overdue_30_days_only")
    void should_not_add_URGENT_OVERDUE_warning_when_overdue_30_days_only() {
        DebtRuleEntry mildOverdueDebt = new DebtRuleEntry(
                "MildBank",
                new BigDecimal("10000"),
                new BigDecimal("12000"),
                365,
                OverdueStatus.OVERDUE_30,
                DebtSourceType.MANUAL,
                null,
                new BigDecimal("20.0")
        );
        RuleInput input = new RuleInput(1, List.of(mildOverdueDebt),
                new BigDecimal("10000"), new BigDecimal("1500"), new BigDecimal("10000"));

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.warnings()).doesNotContain("URGENT_OVERDUE");
    }

    // ===== BIZ_004: OCR low confidence =====

    @Test
    @DisplayName("BIZ_004: should_add_LOW_CONFIDENCE_warning_when_ocr_confidence_below_70")
    void should_add_LOW_CONFIDENCE_warning_when_ocr_confidence_below_70() {
        DebtRuleEntry lowConfidenceDebt = new DebtRuleEntry(
                "OcrBank",
                new BigDecimal("10000"),
                new BigDecimal("12000"),
                365,
                OverdueStatus.NORMAL,
                DebtSourceType.OCR,
                new BigDecimal("60"),
                new BigDecimal("20.0")
        );
        RuleInput input = new RuleInput(1, List.of(lowConfidenceDebt),
                new BigDecimal("10000"), new BigDecimal("1500"), new BigDecimal("10000"));

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.warnings()).contains("LOW_CONFIDENCE");
    }

    @Test
    @DisplayName("BIZ_004: should_not_add_LOW_CONFIDENCE_warning_when_confidence_at_70")
    void should_not_add_LOW_CONFIDENCE_warning_when_confidence_at_70() {
        DebtRuleEntry okConfidenceDebt = new DebtRuleEntry(
                "OcrBank",
                new BigDecimal("10000"),
                new BigDecimal("12000"),
                365,
                OverdueStatus.NORMAL,
                DebtSourceType.OCR,
                new BigDecimal("70"),
                new BigDecimal("20.0")
        );
        RuleInput input = new RuleInput(1, List.of(okConfidenceDebt),
                new BigDecimal("10000"), new BigDecimal("1500"), new BigDecimal("10000"));

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.warnings()).doesNotContain("LOW_CONFIDENCE");
    }

    // ===== Normal pass: all rules pass with no warnings =====

    @Test
    @DisplayName("RULE-OK: should_return_canProceed_true_and_no_warnings_when_all_rules_pass")
    void should_return_canProceed_true_and_no_warnings_when_all_rules_pass() {
        RuleInput input = new RuleInput(
                1,
                List.of(validDebt("NormalBank")),
                new BigDecimal("10000"),
                new BigDecimal("2000"),
                new BigDecimal("10000")
        );

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.canProceed()).isTrue();
        assertThat(result.blocked()).isFalse();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.ruleResults()).isNotEmpty();
    }

    // ===== Multiple debts =====

    @Test
    @DisplayName("RULE-MULTI: should_accumulate_multiple_warnings_when_multiple_debts_have_issues")
    void should_accumulate_multiple_warnings_when_multiple_debts_have_issues() {
        DebtRuleEntry highAprDebt = new DebtRuleEntry(
                "HighRateBank",
                new BigDecimal("10000"),
                new BigDecimal("16000"),
                365,
                OverdueStatus.OVERDUE_60,
                DebtSourceType.OCR,
                new BigDecimal("50"),
                new BigDecimal("40.0")
        );
        DebtRuleEntry anotherDebt = validDebt("NormalBank");

        RuleInput input = new RuleInput(
                2,
                List.of(highAprDebt, anotherDebt),
                new BigDecimal("20000"),
                new BigDecimal("9500"),
                new BigDecimal("10000")
        );

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.canProceed()).isTrue();
        assertThat(result.warnings()).contains("HIGH_INTEREST", "URGENT_OVERDUE", "LOW_CONFIDENCE");
    }

    // ===== Rule result items structure =====

    @Test
    @DisplayName("RULE-STRUCT: should_include_PASS_result_items_for_rules_that_pass")
    void should_include_PASS_result_items_for_rules_that_pass() {
        RuleInput input = new RuleInput(
                1,
                List.of(validDebt("NormalBank")),
                new BigDecimal("10000"),
                new BigDecimal("2000"),
                new BigDecimal("10000")
        );

        RuleResult result = ruleEngine.evaluate(input);

        // DATA rules should all pass
        assertThat(result.ruleResults())
                .anySatisfy(r -> {
                    assertThat(r.ruleId()).isEqualTo("DATA_001");
                    assertThat(r.result()).isEqualTo("PASS");
                });
    }

    @Test
    @DisplayName("RULE-TOTALDEBT: should_not_add_HIGH_TOTAL_DEBT_when_total_debt_exactly_10_million")
    void should_not_add_HIGH_TOTAL_DEBT_when_total_debt_exactly_10_million() {
        RuleInput input = new RuleInput(1, List.of(validDebt("BankA")),
                new BigDecimal("10000000"),
                new BigDecimal("2000"),
                new BigDecimal("50000")
        );

        RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.warnings()).doesNotContain("HIGH_TOTAL_DEBT");
    }
}
