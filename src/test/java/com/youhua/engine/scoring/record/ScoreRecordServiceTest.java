package com.youhua.engine.scoring.record;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.engine.scoring.ScoringEngine.DimensionDetail;
import com.youhua.engine.scoring.ScoringEngine.Recommendation;
import com.youhua.engine.scoring.ScoringEngine.ScoreInput;
import com.youhua.engine.scoring.ScoringEngine.ScoreResult;
import com.youhua.engine.scoring.mapper.ScoreRecordMapper;
import com.youhua.engine.scoring.pmml.UserSegment;
import com.youhua.profile.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreRecordService Tests")
class ScoreRecordServiceTest {

    @Mock
    private ScoreRecordMapper scoreRecordMapper;

    private ScoreRecordService service;

    @BeforeEach
    void setUp() {
        service = new ScoreRecordService(scoreRecordMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("should_record_score_with_correct_fields")
    void should_record_score_with_correct_fields() {
        when(scoreRecordMapper.selectOne(any())).thenReturn(null);
        when(scoreRecordMapper.insert((ScoreRecord) any(ScoreRecord.class))).thenReturn(1);

        ScoreResult result = new ScoreResult(
                new BigDecimal("75.50"), RiskLevel.MEDIUM,
                Recommendation.RESTRUCTURE_RECOMMENDED, "msg", "page",
                List.of(), LocalDateTime.now(),
                UserSegment.DEFAULT, "稳健策略", "1.0", List.of("DIR", "APR"));

        ScoreInput input = new ScoreInput(
                new BigDecimal("5000"), new BigDecimal("10000"),
                new BigDecimal("18.0"), 0, 0, 3, 180L);

        ScoreRecord record = service.recordScore(100001L, result, input);

        ArgumentCaptor<ScoreRecord> captor = ArgumentCaptor.forClass(ScoreRecord.class);
        verify(scoreRecordMapper).insert(captor.capture());

        ScoreRecord captured = captor.getValue();
        assertThat(captured.getUserId()).isEqualTo(100001L);
        assertThat(captured.getStrategyName()).isEqualTo("稳健策略");
        assertThat(captured.getStrategyVersion()).isEqualTo("1.0");
        assertThat(captured.getSegment()).isEqualTo("DEFAULT");
        assertThat(captured.getFinalScore()).isEqualByComparingTo(new BigDecimal("75.50"));
        assertThat(captured.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(captured.getRecommendation()).isEqualTo("RESTRUCTURE_RECOMMENDED");
        assertThat(captured.getScoreDelta()).isNull(); // No previous score
    }

    @Test
    @DisplayName("should_compute_delta_from_previous_score")
    void should_compute_delta_from_previous_score() {
        ScoreRecord previous = new ScoreRecord();
        previous.setFinalScore(new BigDecimal("60.00"));
        when(scoreRecordMapper.selectOne(any())).thenReturn(previous);
        when(scoreRecordMapper.insert((ScoreRecord) any(ScoreRecord.class))).thenReturn(1);

        ScoreResult result = new ScoreResult(
                new BigDecimal("75.50"), RiskLevel.MEDIUM,
                Recommendation.RESTRUCTURE_RECOMMENDED, "msg", "page",
                List.of(), LocalDateTime.now(),
                UserSegment.DEFAULT, "稳健策略", "1.0", List.of());

        ScoreInput input = new ScoreInput(
                new BigDecimal("5000"), new BigDecimal("10000"),
                new BigDecimal("18.0"), 0, 0, 3, 180L);

        service.recordScore(100001L, result, input);

        ArgumentCaptor<ScoreRecord> captor = ArgumentCaptor.forClass(ScoreRecord.class);
        verify(scoreRecordMapper).insert(captor.capture());

        assertThat(captor.getValue().getScoreDelta()).isEqualByComparingTo(new BigDecimal("15.50"));
    }

    @Test
    @DisplayName("should_get_latest_score")
    void should_get_latest_score() {
        ScoreRecord latest = new ScoreRecord();
        latest.setFinalScore(new BigDecimal("80.00"));
        when(scoreRecordMapper.selectOne(any())).thenReturn(latest);

        BigDecimal score = service.getLatestScore(100001L);

        assertThat(score).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("should_return_null_when_no_previous_score")
    void should_return_null_when_no_previous_score() {
        when(scoreRecordMapper.selectOne(any())).thenReturn(null);

        assertThat(service.getLatestScore(100001L)).isNull();
    }

    @Test
    @DisplayName("should_get_history_with_limit")
    void should_get_history_with_limit() {
        when(scoreRecordMapper.selectList(any())).thenReturn(List.of());

        List<ScoreRecord> history = service.getHistory(100001L, 10);

        assertThat(history).isEmpty();
        verify(scoreRecordMapper).selectList(any());
    }
}
