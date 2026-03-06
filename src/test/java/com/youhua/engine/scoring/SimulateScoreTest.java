package com.youhua.engine.scoring;

import com.youhua.engine.scoring.ScoringEngine.ScoreInput;
import com.youhua.engine.scoring.ScoringEngine.ScoreResult;
import com.youhua.engine.scoring.pmml.PmmlScorecardEvaluator;
import com.youhua.engine.scoring.pmml.PmmlStrategyRegistry;
import com.youhua.engine.scoring.pmml.StrategyMetadataLoader;
import com.youhua.engine.scoring.pmml.UserSegmentMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for What-if simulation scenarios.
 * Verifies that modifying inputs produces expected score changes.
 */
@DisplayName("SimulateScore Tests")
class SimulateScoreTest {

    private ScoringEngine scoringEngine;

    @BeforeEach
    void setUp() {
        StrategyMetadataLoader loader = new StrategyMetadataLoader();
        PmmlStrategyRegistry registry = new PmmlStrategyRegistry(loader);
        registry.init();
        PmmlScorecardEvaluator evaluator = new PmmlScorecardEvaluator();
        UserSegmentMatcher matcher = new UserSegmentMatcher();
        scoringEngine = new ScoringEngine(registry, evaluator, matcher);
    }

    @Test
    @DisplayName("should_improve_score_when_paying_off_debt_reduces_ratio")
    void should_improve_score_when_paying_off_debt_reduces_ratio() {
        // Before: ratio=0.62, APR=21.4%, overdue=1(30d), debtCount=4
        ScoreInput before = new ScoreInput(
                new BigDecimal("6200"), new BigDecimal("10000"),
                new BigDecimal("21.4"), 1, 30, 4, 90L);

        // After paying off one debt: ratio=0.40, fewer debts
        ScoreInput after = new ScoreInput(
                new BigDecimal("4000"), new BigDecimal("10000"),
                new BigDecimal("18.0"), 0, 0, 3, 120L);

        ScoreResult beforeResult = scoringEngine.score(before);
        ScoreResult afterResult = scoringEngine.score(after);

        assertThat(afterResult.finalScore()).isGreaterThan(beforeResult.finalScore());
    }

    @Test
    @DisplayName("should_improve_score_when_replacing_high_rate_with_lower")
    void should_improve_score_when_replacing_high_rate_with_lower() {
        // Before: high APR
        ScoreInput before = new ScoreInput(
                new BigDecimal("5000"), new BigDecimal("10000"),
                new BigDecimal("36.01"), 0, 0, 3, 365L);

        // After: lower APR through refinancing
        ScoreInput after = new ScoreInput(
                new BigDecimal("5000"), new BigDecimal("10000"),
                new BigDecimal("12.0"), 0, 0, 3, 365L);

        ScoreResult beforeResult = scoringEngine.score(before);
        ScoreResult afterResult = scoringEngine.score(after);

        assertThat(afterResult.finalScore()).isGreaterThan(beforeResult.finalScore());
        // APR improvement should be the main driver
        BigDecimal aprBefore = beforeResult.dimensions().stream()
                .filter(d -> "weightedApr".equals(d.name()))
                .findFirst().map(ScoringEngine.DimensionDetail::weightedScore).orElse(BigDecimal.ZERO);
        BigDecimal aprAfter = afterResult.dimensions().stream()
                .filter(d -> "weightedApr".equals(d.name()))
                .findFirst().map(ScoringEngine.DimensionDetail::weightedScore).orElse(BigDecimal.ZERO);
        assertThat(aprAfter).isGreaterThan(aprBefore);
    }

    @Test
    @DisplayName("should_show_score_delta_between_before_and_after")
    void should_show_score_delta_between_before_and_after() {
        ScoreInput before = new ScoreInput(
                new BigDecimal("8000"), new BigDecimal("10000"),
                new BigDecimal("30.0"), 2, 45, 5, 120L);

        ScoreInput after = new ScoreInput(
                new BigDecimal("4000"), new BigDecimal("10000"),
                new BigDecimal("15.0"), 0, 0, 2, 365L);

        ScoreResult beforeResult = scoringEngine.score(before);
        ScoreResult afterResult = scoringEngine.score(after);

        BigDecimal delta = afterResult.finalScore().subtract(beforeResult.finalScore());
        assertThat(delta).isPositive();
    }

    @Test
    @DisplayName("should_worsen_score_when_adding_overdue")
    void should_worsen_score_when_adding_overdue() {
        ScoreInput good = new ScoreInput(
                new BigDecimal("5000"), new BigDecimal("10000"),
                new BigDecimal("18.0"), 0, 0, 3, 365L);

        ScoreInput worse = new ScoreInput(
                new BigDecimal("5000"), new BigDecimal("10000"),
                new BigDecimal("18.0"), 3, 90, 3, 365L);

        ScoreResult goodResult = scoringEngine.score(good);
        ScoreResult worseResult = scoringEngine.score(worse);

        assertThat(worseResult.finalScore()).isLessThan(goodResult.finalScore());
    }
}
