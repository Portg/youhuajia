package com.youhua.engine.scoring.pmml;

import com.youhua.engine.scoring.ScoringEngine.ScoreInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserSegmentMatcher Tests")
class UserSegmentMatcherTest {

    private UserSegmentMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new UserSegmentMatcher();
    }

    @Test
    @DisplayName("should_match_HIGH_DEBT_when_debt_income_ratio_above_0_70")
    void should_match_HIGH_DEBT_when_debt_income_ratio_above_0_70() {
        ScoreInput input = new ScoreInput(
                new BigDecimal("8000"), new BigDecimal("10000"),
                new BigDecimal("18.0"), 0, 0, 3, 365L);

        assertThat(matcher.match(input)).isEqualTo(UserSegment.HIGH_DEBT);
    }

    @Test
    @DisplayName("should_match_HIGH_DEBT_when_debt_count_ge_5")
    void should_match_HIGH_DEBT_when_debt_count_ge_5() {
        ScoreInput input = new ScoreInput(
                new BigDecimal("3000"), new BigDecimal("10000"),
                new BigDecimal("18.0"), 0, 0, 5, 365L);

        assertThat(matcher.match(input)).isEqualTo(UserSegment.HIGH_DEBT);
    }

    @Test
    @DisplayName("should_match_MORTGAGE_HEAVY_when_mortgage_ratio_above_50_percent")
    void should_match_MORTGAGE_HEAVY_when_mortgage_ratio_above_50_percent() {
        ScoreInput input = new ScoreInput(
                new BigDecimal("5000"), new BigDecimal("10000"),
                new BigDecimal("8.0"), 0, 0, 3, 365L);

        assertThat(matcher.match(input, new BigDecimal("0.60")))
                .isEqualTo(UserSegment.MORTGAGE_HEAVY);
    }

    @Test
    @DisplayName("should_match_YOUNG_BORROWER_when_few_debts_short_duration")
    void should_match_YOUNG_BORROWER_when_few_debts_short_duration() {
        ScoreInput input = new ScoreInput(
                new BigDecimal("2000"), new BigDecimal("10000"),
                new BigDecimal("15.0"), 0, 0, 1, 180L);

        assertThat(matcher.match(input)).isEqualTo(UserSegment.YOUNG_BORROWER);
    }

    @Test
    @DisplayName("should_match_DEFAULT_when_no_special_conditions")
    void should_match_DEFAULT_when_no_special_conditions() {
        ScoreInput input = new ScoreInput(
                new BigDecimal("3000"), new BigDecimal("10000"),
                new BigDecimal("18.0"), 0, 0, 3, 400L);

        assertThat(matcher.match(input)).isEqualTo(UserSegment.DEFAULT);
    }

    @Test
    @DisplayName("should_prioritize_HIGH_DEBT_over_MORTGAGE_HEAVY")
    void should_prioritize_HIGH_DEBT_over_MORTGAGE_HEAVY() {
        // debtCount=6 triggers HIGH_DEBT even with high mortgage ratio
        ScoreInput input = new ScoreInput(
                new BigDecimal("8000"), new BigDecimal("10000"),
                new BigDecimal("8.0"), 0, 0, 6, 365L);

        assertThat(matcher.match(input, new BigDecimal("0.80")))
                .isEqualTo(UserSegment.HIGH_DEBT);
    }

    @Test
    @DisplayName("should_prioritize_MORTGAGE_HEAVY_over_YOUNG_BORROWER")
    void should_prioritize_MORTGAGE_HEAVY_over_YOUNG_BORROWER() {
        // Few debts + short duration would be YOUNG_BORROWER, but mortgage ratio overrides
        ScoreInput input = new ScoreInput(
                new BigDecimal("3000"), new BigDecimal("10000"),
                new BigDecimal("8.0"), 0, 0, 2, 180L);

        assertThat(matcher.match(input, new BigDecimal("0.70")))
                .isEqualTo(UserSegment.MORTGAGE_HEAVY);
    }

    @Test
    @DisplayName("should_handle_null_income_for_HIGH_DEBT_check")
    void should_handle_null_income_for_HIGH_DEBT_check() {
        // No income → can't compute ratio, but debtCount < 5 → not HIGH_DEBT
        ScoreInput input = new ScoreInput(
                new BigDecimal("5000"), null,
                new BigDecimal("18.0"), 0, 0, 3, 400L);

        assertThat(matcher.match(input)).isEqualTo(UserSegment.DEFAULT);
    }

    @Test
    @DisplayName("should_match_YOUNG_BORROWER_boundary_debtCount_2_avgLoanDays_364")
    void should_match_YOUNG_BORROWER_boundary() {
        ScoreInput input = new ScoreInput(
                new BigDecimal("2000"), new BigDecimal("10000"),
                new BigDecimal("15.0"), 0, 0, 2, 364L);

        assertThat(matcher.match(input)).isEqualTo(UserSegment.YOUNG_BORROWER);
    }

    @Test
    @DisplayName("should_not_match_YOUNG_BORROWER_when_avgLoanDays_ge_365")
    void should_not_match_YOUNG_BORROWER_when_avgLoanDays_ge_365() {
        ScoreInput input = new ScoreInput(
                new BigDecimal("2000"), new BigDecimal("10000"),
                new BigDecimal("15.0"), 0, 0, 2, 365L);

        assertThat(matcher.match(input)).isEqualTo(UserSegment.DEFAULT);
    }
}
