package com.youhua.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.common.util.IncomeCalculator;
import com.youhua.common.util.RequestContextUtil;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.enums.DebtType;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.engine.apr.AprCalculator;
import com.youhua.engine.rules.RuleEngine;
import com.youhua.engine.scoring.ScoringEngine;
import com.youhua.engine.scoring.record.ScoreRecordService;
import com.youhua.infra.log.OperationLogService;
import com.youhua.infra.log.enums.OperationAction;
import com.youhua.infra.log.enums.OperationModule;
import com.youhua.profile.dto.response.FinanceProfileResponse;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.profile.entity.IncomeRecord;
import com.youhua.profile.enums.ProfileGenerationStatus;
import com.youhua.profile.mapper.FinanceProfileMapper;
import com.youhua.profile.mapper.IncomeRecordMapper;
import com.youhua.profile.service.FinanceProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Finance profile service implementation.
 *
 * <p>Orchestrates: debt fetch → income fetch → rule validation → APR calculation
 * → scoring → profile persist.
 *
 * <p>F-01: All monetary/rate values use BigDecimal.
 * <p>F-02: All financial calculations are deterministic (no LLM calls).
 * <p>F-05: No nested @Transactional.
 */
@Slf4j
@Service("financeProfileService")
@RequiredArgsConstructor
public class FinanceProfileServiceImpl implements FinanceProfileService {

    private final FinanceProfileMapper financeProfileMapper;
    private final DebtMapper debtMapper;
    private final IncomeRecordMapper incomeRecordMapper;
    private final AprCalculator aprCalculator;
    private final ScoringEngine scoringEngine;
    private final RuleEngine ruleEngine;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    private final ScoreRecordService scoreRecordService;

    @Value("${youhua.engine.market-base-apr:18}")
    private BigDecimal marketBaseApr;

    /**
     * Intermediate calculation result carried between pipeline steps.
     */
    private record ProfileCalculationData(
            BigDecimal totalDebt,
            BigDecimal monthlyPayment,
            BigDecimal monthlyIncome,
            BigDecimal weightedApr,
            BigDecimal debtIncomeRatio,
            int overdueCount,
            int maxOverdueDays,
            int mortgageCount,
            Long highestAprDebtId,
            ScoringEngine.ScoreResult scoreResult,
            ScoringEngine.ScoreInput scoreInput,
            String scoreDetailJson,
            int debtCount,
            BigDecimal threeYearExtraInterest,
            int avgLoanDays,
            String highestAprCreditor,
            int highInterestDebtCount
    ) {}

    @Override
    public FinanceProfileResponse getFinanceProfile() {
        Long userId = RequestContextUtil.getCurrentUserId();

        LambdaQueryWrapper<FinanceProfile> query = new LambdaQueryWrapper<FinanceProfile>()
                .eq(FinanceProfile::getUserId, userId)
                .eq(FinanceProfile::getDeleted, 0)
                .last("LIMIT 1");

        FinanceProfile profile = financeProfileMapper.selectOne(query);
        if (profile == null) {
            throw new BizException(ErrorCode.PROFILE_NO_CONFIRMED_DEBT, "财务画像尚未生成，请先录入债务并触发计算");
        }

        return toResponse(profile);
    }

    @Override
    public FinanceProfileResponse calculateFinanceProfile() {
        Long userId = RequestContextUtil.getCurrentUserId();

        // Step 1: Load IN_PROFILE debts
        List<Debt> debts = loadInProfileDebts(userId);
        if (debts.isEmpty()) {
            throw new BizException(ErrorCode.PROFILE_NO_CONFIRMED_DEBT);
        }

        // Step 2: Load income records and calculate monthly income
        List<IncomeRecord> incomeRecords = loadIncomeRecords(userId);
        BigDecimal monthlyIncome = calculateMonthlyIncome(incomeRecords);

        // Step 3: Aggregate + score
        ProfileCalculationData data = aggregateDebts(debts, monthlyIncome, userId);

        // Step 4: Create or update FinanceProfile
        FinanceProfile profile = findOrCreateProfile(userId);
        applyCalculationToProfile(profile, userId, data);

        if (profile.getId() == null) {
            financeProfileMapper.insert(profile);
        } else {
            financeProfileMapper.updateById(profile);
        }

        saveOperationLog(userId, OperationAction.CALCULATE, profile.getId(),
                String.format("{\"action\":\"calculateFinanceProfile\",\"debtCount\":%d,\"score\":\"%s\"}",
                        data.debtCount(), data.scoreResult().finalScore()));

        // Record score for effect tracking
        try {
            scoreRecordService.recordScore(userId, data.scoreResult(), data.scoreInput());
        } catch (Exception e) {
            log.warn("[FinanceProfile] Failed to record score for userId={}", userId, e);
        }

        log.debug("[FinanceProfile] calculateFinanceProfile completed: userId={} profileId={}", userId, profile.getId());
        return toResponse(profile);
    }

    @Override
    public void recalculateForUser(Long userId) {
        log.info("[FinanceProfile] recalculateForUser start: userId={}", userId);

        List<Debt> debts = loadInProfileDebts(userId);
        if (debts.isEmpty()) {
            log.info("[FinanceProfile] recalculateForUser skipped — no IN_PROFILE debts: userId={}", userId);
            return;
        }

        List<IncomeRecord> incomeRecords = loadIncomeRecords(userId);
        BigDecimal monthlyIncome = calculateMonthlyIncome(incomeRecords);

        ProfileCalculationData data = aggregateDebts(debts, monthlyIncome, userId);

        FinanceProfile profile = findOrCreateProfile(userId);
        applyCalculationToProfile(profile, userId, data);

        if (profile.getId() == null) {
            financeProfileMapper.insert(profile);
        } else {
            financeProfileMapper.updateById(profile);
        }

        saveOperationLog(userId, OperationAction.CALCULATE, profile.getId(),
                String.format("{\"action\":\"recalculateForUser\",\"debtCount\":%d,\"score\":\"%s\"}",
                        data.debtCount(), data.scoreResult().finalScore()));

        // Record score for effect tracking
        try {
            scoreRecordService.recordScore(userId, data.scoreResult(), data.scoreInput());
        } catch (Exception e) {
            log.warn("[FinanceProfile] Failed to record score for userId={}", userId, e);
        }

        log.info("[FinanceProfile] recalculateForUser completed: userId={} profileId={}", userId, profile.getId());
    }

    // ===================== Private helpers =====================

    /**
     * Aggregates debt data, runs rule engine, calculates APR / debt-income ratio /
     * overdue stats, and runs the scoring engine.
     */
    private ProfileCalculationData aggregateDebts(List<Debt> debts, BigDecimal monthlyIncome, Long userId) {
        BigDecimal totalDebt = debts.stream()
                .filter(d -> d.getPrincipal() != null)
                .map(Debt::getPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyPayment = debts.stream()
                .filter(d -> d.getMonthlyPayment() != null)
                .map(Debt::getMonthlyPayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RuleEngine.DebtRuleEntry> ruleEntries = debts.stream()
                .map(d -> new RuleEngine.DebtRuleEntry(
                        d.getCreditor(),
                        d.getPrincipal() != null ? d.getPrincipal() : BigDecimal.ZERO,
                        d.getTotalRepayment() != null ? d.getTotalRepayment() : BigDecimal.ZERO,
                        d.getLoanDays() != null ? d.getLoanDays() : 0,
                        d.getOverdueStatus(),
                        d.getSourceType(),
                        d.getConfidenceScore(),
                        d.getApr()
                ))
                .toList();

        RuleEngine.RuleInput ruleInput = new RuleEngine.RuleInput(
                debts.size(), ruleEntries, totalDebt, monthlyPayment, monthlyIncome);
        RuleEngine.RuleResult ruleResult = ruleEngine.evaluate(ruleInput);
        if (!ruleResult.warnings().isEmpty()) {
            log.debug("[FinanceProfile] Rule warnings for userId={}: {}", userId, ruleResult.warnings());
        }

        List<AprCalculator.DebtAprEntry> aprEntries = debts.stream()
                .filter(d -> d.getPrincipal() != null && d.getApr() != null)
                .map(d -> new AprCalculator.DebtAprEntry(d.getPrincipal(), d.getApr()))
                .toList();
        BigDecimal weightedApr = aprCalculator.calculateWeightedApr(aprEntries);
        log.debug("[FinanceProfile] userId={} weightedApr={}", userId, weightedApr);

        BigDecimal debtIncomeRatio = null;
        if (monthlyIncome != null && monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            debtIncomeRatio = monthlyPayment.divide(monthlyIncome, 6, RoundingMode.HALF_UP);
        }

        int overdueCount = (int) debts.stream()
                .filter(d -> d.getOverdueStatus() != null && d.getOverdueStatus() != OverdueStatus.NORMAL)
                .count();
        int mortgageCount = (int) debts.stream()
                .filter(d -> DebtType.MORTGAGE.equals(d.getDebtType()))
                .count();
        int maxOverdueDays = debts.stream()
                .filter(d -> d.getOverdueDays() != null)
                .mapToInt(Debt::getOverdueDays)
                .max()
                .orElse(0);
        int avgLoanDays = (int) Math.round(debts.stream()
                .filter(d -> d.getLoanDays() != null)
                .mapToInt(Debt::getLoanDays)
                .average()
                .orElse(0));
        Debt highestAprDebt = debts.stream()
                .filter(d -> d.getApr() != null)
                .max((a, b) -> a.getApr().compareTo(b.getApr()))
                .orElse(null);
        Long highestAprDebtId = highestAprDebt != null ? highestAprDebt.getId() : null;
        String highestAprCreditor = highestAprDebt != null ? highestAprDebt.getCreditor() : null;
        BigDecimal highInterestThreshold = new BigDecimal("24");
        int highInterestDebtCount = (int) debts.stream()
                .filter(d -> d.getApr() != null && d.getApr().compareTo(highInterestThreshold) > 0)
                .count();

        // Calculate 3-year extra interest: sum of principal × (apr - marketBaseApr) / 100 × 3 for debts above market rate
        BigDecimal threeYearExtraInterest = debts.stream()
                .filter(d -> d.getPrincipal() != null && d.getApr() != null
                        && d.getApr().compareTo(marketBaseApr) > 0)
                .map(d -> d.getPrincipal()
                        .multiply(d.getApr().subtract(marketBaseApr))
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(3)))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        log.debug("[FinanceProfile] userId={} threeYearExtraInterest={}", userId, threeYearExtraInterest);

        ScoringEngine.ScoreInput scoreInput = new ScoringEngine.ScoreInput(
                monthlyPayment, monthlyIncome, weightedApr,
                overdueCount, maxOverdueDays, debts.size(), avgLoanDays);
        ScoringEngine.ScoreResult scoreResult = scoringEngine.score(scoreInput);
        log.debug("[FinanceProfile] userId={} finalScore={} riskLevel={}", userId,
                scoreResult.finalScore(), scoreResult.riskLevel());

        String scoreDetailJson;
        try {
            scoreDetailJson = objectMapper.writeValueAsString(scoreResult.dimensions());
        } catch (JsonProcessingException e) {
            log.warn("[FinanceProfile] Failed to serialize score details for userId={}", userId, e);
            scoreDetailJson = "[]";
        }

        return new ProfileCalculationData(
                totalDebt, monthlyPayment, monthlyIncome, weightedApr, debtIncomeRatio,
                overdueCount, maxOverdueDays, mortgageCount, highestAprDebtId, scoreResult, scoreInput, scoreDetailJson, debts.size(),
                threeYearExtraInterest, avgLoanDays, highestAprCreditor, highInterestDebtCount
        );
    }

    /**
     * Applies the aggregated calculation results onto the given profile entity.
     */
    private void applyCalculationToProfile(FinanceProfile profile, Long userId, ProfileCalculationData data) {
        profile.setUserId(userId);
        profile.setTotalDebt(data.totalDebt());
        profile.setDebtCount(data.debtCount());
        profile.setWeightedApr(data.weightedApr());
        profile.setMonthlyPayment(data.monthlyPayment());
        profile.setMonthlyIncome(data.monthlyIncome());
        profile.setDebtIncomeRatio(data.debtIncomeRatio());
        profile.setRestructureScore(data.scoreResult().finalScore());
        profile.setRiskLevel(data.scoreResult().riskLevel());
        // Extract liquidity dimension score from scoring result
        data.scoreResult().dimensions().stream()
                .filter(d -> "liquidity".equals(d.name()))
                .findFirst()
                .ifPresent(d -> profile.setLiquidityScore(d.weightedScore()));
        profile.setOverdueCount(data.overdueCount());
        profile.setMaxOverdueDays(data.maxOverdueDays());
        profile.setMortgageCount(data.mortgageCount());
        profile.setHighestAprDebtId(data.highestAprDebtId());
        profile.setScoreDetailJson(data.scoreDetailJson());
        profile.setThreeYearExtraInterest(data.threeYearExtraInterest());
        profile.setAvgLoanDays(data.avgLoanDays());
        profile.setHighestAprCreditor(data.highestAprCreditor());
        profile.setHighInterestDebtCount(data.highInterestDebtCount());
        profile.setGenerationStatus(ProfileGenerationStatus.COMPLETED);
        profile.setLastCalculatedTime(LocalDateTime.now());
    }

    private List<Debt> loadInProfileDebts(Long userId) {
        LambdaQueryWrapper<Debt> query = new LambdaQueryWrapper<Debt>()
                .eq(Debt::getUserId, userId)
                .eq(Debt::getStatus, DebtStatus.IN_PROFILE)
                .eq(Debt::getDeleted, 0);
        return debtMapper.selectList(query);
    }

    private List<IncomeRecord> loadIncomeRecords(Long userId) {
        LambdaQueryWrapper<IncomeRecord> query = new LambdaQueryWrapper<IncomeRecord>()
                .eq(IncomeRecord::getUserId, userId)
                .eq(IncomeRecord::getDeleted, 0);
        return incomeRecordMapper.selectList(query);
    }

    private BigDecimal calculateMonthlyIncome(List<IncomeRecord> records) {
        return IncomeCalculator.sumMonthlyIncome(records);
    }

    private FinanceProfile findOrCreateProfile(Long userId) {
        LambdaQueryWrapper<FinanceProfile> query = new LambdaQueryWrapper<FinanceProfile>()
                .eq(FinanceProfile::getUserId, userId)
                .eq(FinanceProfile::getDeleted, 0)
                .last("LIMIT 1");
        FinanceProfile existing = financeProfileMapper.selectOne(query);
        return existing != null ? existing : new FinanceProfile();
    }

    private void saveOperationLog(Long userId, OperationAction action, Long targetId, String detailJson) {
        operationLogService.record(userId, OperationModule.PROFILE, action, "FinanceProfile", targetId, detailJson);
    }

    private FinanceProfileResponse toResponse(FinanceProfile profile) {
        List<Map<String, Object>> scoreDimensions = null;
        if (profile.getScoreDetailJson() != null) {
            try {
                scoreDimensions = objectMapper.readValue(profile.getScoreDetailJson(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (JsonProcessingException e) {
                log.warn("[FinanceProfile] Failed to deserialize scoreDetail for userId={}", profile.getUserId());
            }
        }

        return FinanceProfileResponse.builder()
                .name("users/" + profile.getUserId() + "/finance-profile")
                .totalDebt(profile.getTotalDebt())
                .debtCount(profile.getDebtCount())
                .weightedApr(profile.getWeightedApr())
                .monthlyPayment(profile.getMonthlyPayment())
                .monthlyIncome(profile.getMonthlyIncome())
                .debtIncomeRatio(profile.getDebtIncomeRatio())
                .liquidityScore(profile.getLiquidityScore())
                .restructureScore(profile.getRestructureScore())
                .riskLevel(profile.getRiskLevel())
                .scoreDimensions(scoreDimensions)
                .lastCalculateTime(profile.getLastCalculatedTime())
                .threeYearExtraInterest(profile.getThreeYearExtraInterest())
                .avgLoanDays(profile.getAvgLoanDays())
                .highestAprCreditor(profile.getHighestAprCreditor())
                .highInterestDebtCount(profile.getHighInterestDebtCount())
                .overdueCount(profile.getOverdueCount())
                .maxOverdueDays(profile.getMaxOverdueDays())
                .mortgageCount(profile.getMortgageCount())
                .build();
    }
}
