package com.youhua.engine.scoring.record;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.engine.scoring.ScoringEngine.ScoreInput;
import com.youhua.engine.scoring.ScoringEngine.ScoreResult;
import com.youhua.engine.scoring.mapper.ScoreRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for recording scoring results and computing score deltas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreRecordService {

    private final ScoreRecordMapper scoreRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * Record a scoring result. Computes delta from the user's previous score.
     */
    public ScoreRecord recordScore(Long userId, ScoreResult result, ScoreInput input) {
        ScoreRecord record = new ScoreRecord();
        record.setUserId(userId);
        record.setStrategyName(result.strategyName() != null ? result.strategyName() : "unknown");
        record.setStrategyVersion(result.strategyVersion());
        record.setSegment(result.segment() != null ? result.segment().getValue() : "DEFAULT");
        record.setFinalScore(result.finalScore());
        record.setRiskLevel(result.riskLevel().name());
        record.setRecommendation(result.recommendation().name());
        record.setCreateTime(LocalDateTime.now());
        record.setDeleted(0);

        // Serialize JSON fields
        try {
            record.setDimensionScoresJson(objectMapper.writeValueAsString(result.dimensions()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize dimension scores for userId={}", userId, e);
            record.setDimensionScoresJson("[]");
        }

        try {
            record.setReasonCodesJson(objectMapper.writeValueAsString(result.reasonCodes()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize reason codes for userId={}", userId, e);
            record.setReasonCodesJson("[]");
        }

        try {
            record.setInputSnapshotJson(objectMapper.writeValueAsString(input));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize score input for userId={}", userId, e);
            record.setInputSnapshotJson("{}");
        }

        // Compute delta from previous score
        BigDecimal previousScore = getLatestScore(userId);
        if (previousScore != null) {
            record.setScoreDelta(result.finalScore().subtract(previousScore));
        }

        scoreRecordMapper.insert(record);
        log.debug("Score recorded: userId={}, score={}, delta={}, strategy={}@{}",
                userId, result.finalScore(), record.getScoreDelta(),
                record.getStrategyName(), record.getStrategyVersion());

        return record;
    }

    /**
     * Get the latest score for a user.
     */
    public BigDecimal getLatestScore(Long userId) {
        LambdaQueryWrapper<ScoreRecord> query = new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getUserId, userId)
                .eq(ScoreRecord::getDeleted, 0)
                .orderByDesc(ScoreRecord::getCreateTime)
                .last("LIMIT 1");

        ScoreRecord latest = scoreRecordMapper.selectOne(query);
        return latest != null ? latest.getFinalScore() : null;
    }

    /**
     * Get scoring history for a user.
     */
    public List<ScoreRecord> getHistory(Long userId, int limit) {
        LambdaQueryWrapper<ScoreRecord> query = new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getUserId, userId)
                .eq(ScoreRecord::getDeleted, 0)
                .orderByDesc(ScoreRecord::getCreateTime)
                .last("LIMIT " + Math.min(limit, 100));

        return scoreRecordMapper.selectList(query);
    }
}
