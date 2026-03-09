package com.youhua.engine.scoring;

import com.youhua.engine.scoring.PreAuditEngine.PreAuditInput;
import com.youhua.engine.scoring.PreAuditEngine.PreAuditResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PreAuditEngine.
 * Coverage: dimension scoring, probability clamping, suggestion matching, config fallback.
 *
 * <p>Base probability = 50, clamped to [35, 92].
 * Dimensions: SCORE(min-threshold), DIR(max-threshold), OVD(boolean), CST(max-threshold), APR_HIGH(max-threshold).
 */
@DisplayName("PreAuditEngine Tests")
class PreAuditEngineTest {

    private PreAuditEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PreAuditEngine();
        engine.init(); // loads preaudit.meta.yml from classpath
    }

    // ===== Helper =====

    private PreAuditInput input(String score, String dir, boolean overdue, int debtCount, String highAprRatio) {
        return new PreAuditInput(
                new BigDecimal(score),
                new BigDecimal(dir),
                overdue,
                debtCount,
                new BigDecimal(highAprRatio)
        );
    }

    // ===== Normal Scenarios =====

    @Nested
    @DisplayName("Probability Calculation")
    class ProbabilityTests {

        @Test
        @DisplayName("should_return_high_probability_when_excellent_profile")
        void should_return_high_probability_when_excellent_profile() {
            // score=85→+20, dir=0.25→+10, noOverdue→+8, debtCount=1→+5, highApr=0.00→+5
            // 50 + 20 + 10 + 8 + 5 + 5 = 98 → clamped to 92
            PreAuditResult result = engine.estimate(input("85", "0.25", false, 1, "0.00"));
            assertThat(result.probability()).isEqualTo(92);
        }

        @Test
        @DisplayName("should_return_low_probability_when_poor_profile")
        void should_return_low_probability_when_poor_profile() {
            // score=30→-10, dir=0.85→-5, hasOverdue→-12, debtCount=7→-10, highApr=0.80→-8
            // 50 - 10 - 5 - 12 - 10 - 8 = 5 → clamped to 35
            PreAuditResult result = engine.estimate(input("30", "0.85", true, 7, "0.80"));
            assertThat(result.probability()).isEqualTo(35);
        }

        @Test
        @DisplayName("should_return_base_probability_when_neutral_profile")
        void should_return_base_probability_when_neutral_profile() {
            // score=65→+10, dir=0.65→0, noOverdue→+8, debtCount=3→0, highApr=0.20→0
            // 50 + 10 + 0 + 8 + 0 + 0 = 68
            PreAuditResult result = engine.estimate(input("65", "0.65", false, 3, "0.20"));
            assertThat(result.probability()).isEqualTo(68);
        }

        @Test
        @DisplayName("should_apply_overdue_penalty_correctly")
        void should_apply_overdue_penalty_correctly() {
            // Same as neutral but with overdue: +8 → -12, diff = -20
            PreAuditResult noOverdue = engine.estimate(input("65", "0.65", false, 3, "0.20"));
            PreAuditResult hasOverdue = engine.estimate(input("65", "0.65", true, 3, "0.20"));
            assertThat(noOverdue.probability() - hasOverdue.probability()).isEqualTo(20);
        }
    }

    // ===== Boundary Values =====

    @Nested
    @DisplayName("Boundary Values")
    class BoundaryTests {

        @Test
        @DisplayName("should_match_score_80_threshold_exactly")
        void should_match_score_80_threshold_exactly() {
            // score=80 → min:80 → delta +20
            PreAuditResult at80 = engine.estimate(input("80", "0.50", false, 3, "0.20"));
            // score=79 → min:60 → delta +10
            PreAuditResult at79 = engine.estimate(input("79", "0.50", false, 3, "0.20"));
            assertThat(at80.probability() - at79.probability()).isEqualTo(10);
        }

        @Test
        @DisplayName("should_match_dir_030_threshold_exactly")
        void should_match_dir_030_threshold_exactly() {
            // dir=0.30 → max:0.30 → delta +10
            PreAuditResult at30 = engine.estimate(input("65", "0.30", false, 3, "0.20"));
            // dir=0.31 → max:0.50 → delta +5
            PreAuditResult at31 = engine.estimate(input("65", "0.31", false, 3, "0.20"));
            assertThat(at30.probability() - at31.probability()).isEqualTo(5);
        }

        @Test
        @DisplayName("should_match_debt_count_threshold_exactly")
        void should_match_debt_count_threshold_exactly() {
            // debtCount=2 → max:2 → delta +5
            PreAuditResult at2 = engine.estimate(input("65", "0.50", false, 2, "0.20"));
            // debtCount=3 → max:4 → delta 0
            PreAuditResult at3 = engine.estimate(input("65", "0.50", false, 3, "0.20"));
            assertThat(at2.probability() - at3.probability()).isEqualTo(5);
        }

        @Test
        @DisplayName("should_match_high_apr_ratio_000_exactly")
        void should_match_high_apr_ratio_000_exactly() {
            // highAprRatio=0.00 → max:0.00 → delta +5
            PreAuditResult at0 = engine.estimate(input("65", "0.50", false, 3, "0.00"));
            // highAprRatio=0.01 → max:0.30 → delta 0
            PreAuditResult at01 = engine.estimate(input("65", "0.50", false, 3, "0.01"));
            assertThat(at0.probability() - at01.probability()).isEqualTo(5);
        }
    }

    // ===== Clamping =====

    @Nested
    @DisplayName("Probability Clamping")
    class ClampingTests {

        @Test
        @DisplayName("should_clamp_to_max_92_when_sum_exceeds")
        void should_clamp_to_max_92_when_sum_exceeds() {
            // All maximal deltas: 50+20+10+8+5+5=98 → 92
            PreAuditResult result = engine.estimate(input("90", "0.20", false, 1, "0.00"));
            assertThat(result.probability()).isEqualTo(92);
        }

        @Test
        @DisplayName("should_clamp_to_min_35_when_sum_is_below")
        void should_clamp_to_min_35_when_sum_is_below() {
            // All minimal deltas: 50-10-10-12-10-8=0 → 35
            PreAuditResult result = engine.estimate(input("10", "1.50", true, 8, "0.90"));
            assertThat(result.probability()).isEqualTo(35);
        }

        @Test
        @DisplayName("should_return_within_bounds_for_any_input")
        void should_return_within_bounds_for_any_input() {
            PreAuditResult result = engine.estimate(input("0", "999", true, 100, "1.00"));
            assertThat(result.probability()).isBetween(35, 92);
        }
    }

    // ===== Suggestions =====

    @Nested
    @DisplayName("Suggestion Matching")
    class SuggestionTests {

        @Test
        @DisplayName("should_suggest_overdue_action_when_has_overdue")
        void should_suggest_overdue_action_when_has_overdue() {
            PreAuditResult result = engine.estimate(input("65", "0.50", true, 3, "0.20"));
            assertThat(result.suggestions()).anyMatch(s -> s.contains("逾期"));
        }

        @Test
        @DisplayName("should_suggest_high_apr_action_when_ratio_gt_30")
        void should_suggest_high_apr_action_when_ratio_gt_30() {
            PreAuditResult result = engine.estimate(input("65", "0.50", false, 3, "0.50"));
            assertThat(result.suggestions()).anyMatch(s -> s.contains("高利率"));
        }

        @Test
        @DisplayName("should_suggest_dir_action_when_dir_gt_70")
        void should_suggest_dir_action_when_dir_gt_70() {
            PreAuditResult result = engine.estimate(input("65", "0.80", false, 3, "0.20"));
            assertThat(result.suggestions()).anyMatch(s -> s.contains("月供占收入"));
        }

        @Test
        @DisplayName("should_suggest_debt_consolidation_when_count_gt_4")
        void should_suggest_debt_consolidation_when_count_gt_4() {
            PreAuditResult result = engine.estimate(input("65", "0.50", false, 6, "0.20"));
            assertThat(result.suggestions()).anyMatch(s -> s.contains("负债笔数"));
        }

        @Test
        @DisplayName("should_suggest_positive_message_when_score_ge_70")
        void should_suggest_positive_message_when_score_ge_70() {
            PreAuditResult result = engine.estimate(input("75", "0.40", false, 2, "0.10"));
            assertThat(result.suggestions()).anyMatch(s -> s.contains("良好状态"));
        }

        @Test
        @DisplayName("should_limit_suggestions_to_max_3")
        void should_limit_suggestions_to_max_3() {
            // Trigger many conditions: overdue + high APR + high DIR + many debts
            PreAuditResult result = engine.estimate(input("45", "0.80", true, 6, "0.70"));
            assertThat(result.suggestions()).hasSizeLessThanOrEqualTo(3);
        }

        @Test
        @DisplayName("should_return_fallback_suggestion_when_no_conditions_match")
        void should_return_fallback_suggestion_when_no_conditions_match() {
            // score=55 (not ≥70, not <50), dir=0.50 (not >0.70), no overdue,
            // debtCount=3 (not >4), highApr=0.20 (not >0.30)
            // Only LOW_DIR_NO_OVERDUE (dir≤0.50 && !overdue) might match → but that IS a match
            // Let me pick dir=0.60 to avoid LOW_DIR_NO_OVERDUE
            PreAuditResult result = engine.estimate(input("55", "0.60", false, 3, "0.20"));
            assertThat(result.suggestions()).hasSize(1);
            assertThat(result.suggestions().get(0)).contains("收入证明和还款记录");
        }

        @Test
        @DisplayName("should_match_low_dir_no_overdue_condition")
        void should_match_low_dir_no_overdue_condition() {
            // dir=0.40 (≤0.50) && no overdue, score=55 (not ≥70, not <50)
            PreAuditResult result = engine.estimate(input("55", "0.40", false, 3, "0.20"));
            assertThat(result.suggestions()).anyMatch(s -> s.contains("通过率较高"));
        }

        @Test
        @DisplayName("should_suggest_improvement_plan_when_score_lt_50")
        void should_suggest_improvement_plan_when_score_lt_50() {
            PreAuditResult result = engine.estimate(input("45", "0.50", false, 3, "0.20"));
            assertThat(result.suggestions()).anyMatch(s -> s.contains("30 天改善计划"));
        }
    }

    // ===== Config Fallback =====

    @Nested
    @DisplayName("Config Fallback")
    class ConfigFallbackTests {

        @Test
        @DisplayName("should_return_default_result_when_config_is_null")
        void should_return_default_result_when_config_is_null() {
            PreAuditEngine unconfigured = new PreAuditEngine();
            // Don't call init() — config remains null
            PreAuditResult result = unconfigured.estimate(input("75", "0.40", false, 2, "0.10"));
            assertThat(result.probability()).isEqualTo(50);
            assertThat(result.suggestions()).hasSize(1);
            assertThat(result.suggestions().get(0)).contains("收入证明和还款记录");
        }

        @Test
        @DisplayName("should_load_config_successfully")
        void should_load_config_successfully() {
            assertThat(engine.getConfig()).isNotNull();
            assertThat(engine.getConfig().getVersion()).isEqualTo("1.0");
            assertThat(engine.getConfig().getBaseProbability()).isEqualTo(50);
            assertThat(engine.getConfig().getMinProbability()).isEqualTo(35);
            assertThat(engine.getConfig().getMaxProbability()).isEqualTo(92);
        }
    }
}
