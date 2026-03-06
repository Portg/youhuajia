package com.youhua.engine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.common.util.IncomeCalculator;
import com.youhua.common.util.RequestContextUtil;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.engine.apr.AprCalculator;
import com.youhua.engine.dto.request.AssessPressureRequest;
import com.youhua.engine.dto.request.CalculateAprRequest;
import com.youhua.engine.dto.request.CompareStrategiesRequest;
import com.youhua.engine.dto.request.SimulateRateRequest;
import com.youhua.engine.dto.request.SimulateScoreRequest;
import com.youhua.engine.dto.request.SimulateScoreRequest.SimulateAction;
import com.youhua.engine.dto.response.AssessPressureResponse;
import com.youhua.engine.dto.response.AssessPressureResponse.PressureLevel;
import com.youhua.engine.dto.response.CalculateAprResponse;
import com.youhua.engine.dto.response.CompareStrategiesResponse;
import com.youhua.engine.dto.response.SimulateRateResponse;
import com.youhua.engine.dto.response.SimulateScoreResponse;
import com.youhua.engine.scoring.ScoringEngine;
import com.youhua.engine.scoring.ScoringEngine.DimensionDetail;
import com.youhua.engine.scoring.ScoringEngine.ScoreInput;
import com.youhua.engine.scoring.ScoringEngine.ScoreResult;
import com.youhua.engine.scoring.pmml.PmmlScorecardEvaluator;
import com.youhua.engine.scoring.pmml.PmmlStrategyRegistry;
import com.youhua.engine.scoring.pmml.PmmlStrategyRegistry.StrategyEntry;
import com.youhua.engine.scoring.pmml.StrategyMetadata;
import com.youhua.engine.scoring.pmml.UserSegment;
import com.youhua.engine.service.EngineService;
import com.youhua.profile.entity.IncomeRecord;
import com.youhua.profile.mapper.IncomeRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EngineServiceImpl implements EngineService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");
    private static final BigDecimal THREE_YEAR_MONTHS = new BigDecimal("36");
    private static final BigDecimal THREE_SIXTY_FIVE = new BigDecimal("365");

    private static final BigDecimal PRESSURE_HEALTHY_MAX = new BigDecimal("0.3");
    private static final BigDecimal PRESSURE_MODERATE_MAX = new BigDecimal("0.5");
    private static final BigDecimal PRESSURE_HEAVY_MAX = new BigDecimal("0.7");

    private final AprCalculator aprCalculator;
    private final ScoringEngine scoringEngine;
    private final DebtMapper debtMapper;
    private final IncomeRecordMapper incomeRecordMapper;
    private final PmmlStrategyRegistry strategyRegistry;
    private final PmmlScorecardEvaluator pmmlEvaluator;

    @Override
    public CalculateAprResponse calculateApr(CalculateAprRequest request) {
        BigDecimal principal = request.getPrincipal();
        BigDecimal totalRepayment = request.getTotalRepayment();
        int loanDays = request.getLoanDays();

        BigDecimal apr = aprCalculator.calculateApr(principal, totalRepayment, loanDays);
        BigDecimal dailyRate = aprCalculator.calculateDailyRate(principal, totalRepayment, loanDays);
        BigDecimal totalInterest = aprCalculator.calculateTotalInterest(principal, totalRepayment);

        log.debug("calculateApr: principal={}, totalRepayment={}, loanDays={}, apr={}, dailyRate={}, totalInterest={}",
                principal, totalRepayment, loanDays, apr, dailyRate, totalInterest);

        return CalculateAprResponse.builder()
                .apr(apr)
                .dailyRate(dailyRate)
                .totalInterest(totalInterest)
                .build();
    }

    @Override
    public AssessPressureResponse assessPressure(AssessPressureRequest request) {
        BigDecimal monthlyPayment = request.getMonthlyPayment();
        BigDecimal monthlyIncome = request.getMonthlyIncome();

        if (monthlyIncome.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("assessPressure: income=0, returning SEVERE");
            return AssessPressureResponse.builder()
                    .pressureIndex(HUNDRED)
                    .level(PressureLevel.SEVERE)
                    .ratio(null)
                    .hint("录入具体债务，获得精确分析和优化建议")
                    .build();
        }

        BigDecimal ratio = monthlyPayment.divide(monthlyIncome, SCALE, ROUNDING);
        BigDecimal pressureIndex = ratio.multiply(HUNDRED).min(HUNDRED).setScale(SCALE, ROUNDING);

        PressureLevel level;
        String hint;
        if (ratio.compareTo(PRESSURE_HEALTHY_MAX) <= 0) {
            level = PressureLevel.HEALTHY;
            hint = "财务状况良好，继续保持良好的还款习惯";
        } else if (ratio.compareTo(PRESSURE_MODERATE_MAX) <= 0) {
            level = PressureLevel.MODERATE;
            hint = "负债比例适中，建议关注债务结构优化空间";
        } else if (ratio.compareTo(PRESSURE_HEAVY_MAX) <= 0) {
            level = PressureLevel.HEAVY;
            hint = "月供压力较大，建议录入具体债务获取优化方案";
        } else {
            level = PressureLevel.SEVERE;
            hint = "录入具体债务，获得精确分析和优化建议";
        }

        log.debug("assessPressure: monthlyPayment={}, monthlyIncome={}, ratio={}, pressureIndex={}, level={}",
                monthlyPayment, monthlyIncome, ratio, pressureIndex, level);

        return AssessPressureResponse.builder()
                .pressureIndex(pressureIndex)
                .level(level)
                .ratio(ratio)
                .hint(hint)
                .build();
    }

    @Override
    public SimulateRateResponse simulateRate(SimulateRateRequest request) {
        BigDecimal totalPrincipal = request.getTotalPrincipal();
        BigDecimal currentApr = request.getCurrentWeightedApr();
        BigDecimal targetApr = request.getTargetApr();
        BigDecimal avgLoanDaysBd = new BigDecimal(request.getAvgLoanDays());

        // Simplified monthly payment formula:
        // monthlyPayment = totalPrincipal * (1 + apr/100 * avgLoanDays/365) / (avgLoanDays/30)
        BigDecimal currentMonthlyPayment = calcMonthlyPayment(totalPrincipal, currentApr, avgLoanDaysBd);
        BigDecimal targetMonthlyPayment = calcMonthlyPayment(totalPrincipal, targetApr, avgLoanDaysBd);

        BigDecimal monthlySaving = currentMonthlyPayment.subtract(targetMonthlyPayment).setScale(SCALE, ROUNDING);
        BigDecimal threeYearSaving = monthlySaving.multiply(THREE_YEAR_MONTHS).setScale(SCALE, ROUNDING);

        BigDecimal currentIncomeRatio = null;
        BigDecimal targetIncomeRatio = null;
        BigDecimal monthlyIncome = request.getMonthlyIncome();
        if (monthlyIncome != null && monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            currentIncomeRatio = currentMonthlyPayment.divide(monthlyIncome, SCALE, ROUNDING);
            targetIncomeRatio = targetMonthlyPayment.divide(monthlyIncome, SCALE, ROUNDING);
        }

        log.debug("simulateRate: totalPrincipal={}, currentApr={}, targetApr={}, avgLoanDays={}, " +
                        "currentMonthly={}, targetMonthly={}, monthlySaving={}, threeYearSaving={}",
                totalPrincipal, currentApr, targetApr, avgLoanDaysBd,
                currentMonthlyPayment, targetMonthlyPayment, monthlySaving, threeYearSaving);

        return SimulateRateResponse.builder()
                .currentMonthlyPayment(currentMonthlyPayment)
                .targetMonthlyPayment(targetMonthlyPayment)
                .monthlySaving(monthlySaving)
                .threeYearSaving(threeYearSaving)
                .currentIncomeRatio(currentIncomeRatio)
                .targetIncomeRatio(targetIncomeRatio)
                .disclaimer("以上为简化估算，实际月供因还款方式不同可能存在差异")
                .build();
    }

    @Override
    public SimulateScoreResponse simulateScore(SimulateScoreRequest request) {
        Long userId = RequestContextUtil.getCurrentUserId();

        // Load current debts
        List<Debt> debts = debtMapper.selectList(new LambdaQueryWrapper<Debt>()
                .eq(Debt::getUserId, userId)
                .eq(Debt::getStatus, DebtStatus.IN_PROFILE)
                .eq(Debt::getDeleted, 0));

        if (debts.isEmpty()) {
            throw new BizException(ErrorCode.PROFILE_NO_CONFIRMED_DEBT);
        }

        // Calculate monthly income
        List<IncomeRecord> incomeRecords = incomeRecordMapper.selectList(
                new LambdaQueryWrapper<IncomeRecord>()
                        .eq(IncomeRecord::getUserId, userId)
                        .eq(IncomeRecord::getDeleted, 0));
        BigDecimal monthlyIncome = calcMonthlyIncome(incomeRecords);

        // Current score
        ScoreInput currentInput = buildScoreInput(debts, monthlyIncome);
        ScoreResult currentResult = scoringEngine.score(currentInput);

        // Apply simulated actions to a copy of debts
        List<Debt> simulatedDebts = applySimulations(debts, request.getActions());

        // Simulated score
        ScoreInput simulatedInput = buildScoreInput(simulatedDebts, monthlyIncome);
        ScoreResult simulatedResult = scoringEngine.score(simulatedInput);

        // Build dimension deltas
        List<SimulateScoreResponse.DimensionDelta> deltas = buildDimensionDeltas(
                currentResult.dimensions(), simulatedResult.dimensions());

        return SimulateScoreResponse.builder()
                .current(SimulateScoreResponse.ScoreSummary.builder()
                        .finalScore(currentResult.finalScore())
                        .riskLevel(currentResult.riskLevel().name())
                        .recommendation(currentResult.recommendation().name())
                        .dimensions(currentResult.dimensions())
                        .build())
                .simulated(SimulateScoreResponse.ScoreSummary.builder()
                        .finalScore(simulatedResult.finalScore())
                        .riskLevel(simulatedResult.riskLevel().name())
                        .recommendation(simulatedResult.recommendation().name())
                        .dimensions(simulatedResult.dimensions())
                        .build())
                .dimensionDeltas(deltas)
                .build();
    }

    @Override
    public CompareStrategiesResponse compareStrategies(CompareStrategiesRequest request) {
        UserSegment segA;
        UserSegment segB;
        try {
            segA = UserSegment.valueOf(request.getSegmentA().toUpperCase());
            segB = UserSegment.valueOf(request.getSegmentB().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.STRATEGY_NOT_FOUND, "Unknown segment");
        }

        StrategyEntry entryA = strategyRegistry.getStrategy(segA);
        StrategyEntry entryB = strategyRegistry.getStrategy(segB);

        // Build ScoreInput from user's actual data
        Long userId = request.getUserId();
        List<Debt> debts = debtMapper.selectList(new LambdaQueryWrapper<Debt>()
                .eq(Debt::getUserId, userId)
                .eq(Debt::getStatus, DebtStatus.IN_PROFILE)
                .eq(Debt::getDeleted, 0));

        List<IncomeRecord> incomeRecords = incomeRecordMapper.selectList(
                new LambdaQueryWrapper<IncomeRecord>()
                        .eq(IncomeRecord::getUserId, userId)
                        .eq(IncomeRecord::getDeleted, 0));
        BigDecimal monthlyIncome = calcMonthlyIncome(incomeRecords);

        ScoreInput scoreInput = debts.isEmpty()
                ? new ScoreInput(BigDecimal.ZERO, monthlyIncome, BigDecimal.ZERO, 0, 0, 0, 0L)
                : buildScoreInput(debts, monthlyIncome);

        PmmlScorecardEvaluator.PmmlEvalResult resultA = pmmlEvaluator.evaluate(entryA.evaluator(), scoreInput);
        PmmlScorecardEvaluator.PmmlEvalResult resultB = pmmlEvaluator.evaluate(entryB.evaluator(), scoreInput);

        StrategyMetadata metaA = entryA.metadata();
        StrategyMetadata metaB = entryB.metadata();

        return CompareStrategiesResponse.builder()
                .strategyA(CompareStrategiesResponse.StrategyScoreSummary.builder()
                        .segment(segA.name())
                        .strategyName(metaA != null ? metaA.getStrategyName() : "unknown")
                        .version(metaA != null ? metaA.getVersion() : "unknown")
                        .finalScore(resultA.finalScore())
                        .riskLevel(pmmlEvaluator.mapRiskLevel(resultA.finalScore(), metaA).name())
                        .recommendation(null)
                        .build())
                .strategyB(CompareStrategiesResponse.StrategyScoreSummary.builder()
                        .segment(segB.name())
                        .strategyName(metaB != null ? metaB.getStrategyName() : "unknown")
                        .version(metaB != null ? metaB.getVersion() : "unknown")
                        .finalScore(resultB.finalScore())
                        .riskLevel(pmmlEvaluator.mapRiskLevel(resultB.finalScore(), metaB).name())
                        .recommendation(null)
                        .build())
                .scoreDelta(resultA.finalScore().subtract(resultB.finalScore()))
                .build();
    }

    private ScoreInput buildScoreInput(List<Debt> debts, BigDecimal monthlyIncome) {
        BigDecimal monthlyPayment = debts.stream()
                .filter(d -> d.getMonthlyPayment() != null)
                .map(Debt::getMonthlyPayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AprCalculator.DebtAprEntry> aprEntries = debts.stream()
                .filter(d -> d.getPrincipal() != null && d.getApr() != null)
                .map(d -> new AprCalculator.DebtAprEntry(d.getPrincipal(), d.getApr()))
                .toList();
        BigDecimal weightedApr = aprCalculator.calculateWeightedApr(aprEntries);

        int overdueCount = (int) debts.stream()
                .filter(d -> d.getOverdueStatus() != null && d.getOverdueStatus() != OverdueStatus.NORMAL)
                .count();
        int maxOverdueDays = debts.stream()
                .filter(d -> d.getOverdueDays() != null)
                .mapToInt(Debt::getOverdueDays)
                .max().orElse(0);
        long avgLoanDays = (long) debts.stream()
                .filter(d -> d.getLoanDays() != null)
                .mapToInt(Debt::getLoanDays)
                .average().orElse(0);

        return new ScoreInput(monthlyPayment, monthlyIncome, weightedApr,
                overdueCount, maxOverdueDays, debts.size(), avgLoanDays);
    }

    private List<Debt> applySimulations(List<Debt> originals, List<SimulateAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return originals;
        }

        // Deep copy debts
        List<Debt> copy = new ArrayList<>();
        for (Debt d : originals) {
            Debt c = new Debt();
            c.setId(d.getId());
            c.setPrincipal(d.getPrincipal());
            c.setTotalRepayment(d.getTotalRepayment());
            c.setApr(d.getApr());
            c.setMonthlyPayment(d.getMonthlyPayment());
            c.setLoanDays(d.getLoanDays());
            c.setOverdueStatus(d.getOverdueStatus());
            c.setOverdueDays(d.getOverdueDays());
            c.setDebtType(d.getDebtType());
            c.setCreditor(d.getCreditor());
            c.setRemainingPrincipal(d.getRemainingPrincipal());
            copy.add(c);
        }

        for (SimulateAction action : actions) {
            switch (action.getType()) {
                case PAYOFF -> copy.removeIf(d -> d.getId().equals(action.getDebtId()));
                case REDUCE_PRINCIPAL -> copy.stream()
                        .filter(d -> d.getId().equals(action.getDebtId()))
                        .findFirst()
                        .ifPresent(d -> {
                            BigDecimal reduced = d.getPrincipal().subtract(action.getValue());
                            if (reduced.compareTo(BigDecimal.ZERO) > 0) {
                                d.setPrincipal(reduced);
                                // Proportionally reduce monthly payment
                                if (d.getMonthlyPayment() != null && d.getPrincipal().compareTo(BigDecimal.ZERO) > 0) {
                                    BigDecimal ratio = reduced.divide(
                                            d.getPrincipal().add(action.getValue()), 10, ROUNDING);
                                    d.setMonthlyPayment(d.getMonthlyPayment().multiply(ratio).setScale(SCALE, ROUNDING));
                                }
                            } else {
                                copy.removeIf(dd -> dd.getId().equals(action.getDebtId()));
                            }
                        });
                case REPLACE_RATE -> copy.stream()
                        .filter(d -> d.getId().equals(action.getDebtId()))
                        .findFirst()
                        .ifPresent(d -> d.setApr(action.getValue()));
            }
        }

        return copy;
    }

    private BigDecimal calcMonthlyIncome(List<IncomeRecord> records) {
        return IncomeCalculator.sumMonthlyIncome(records);
    }

    private List<SimulateScoreResponse.DimensionDelta> buildDimensionDeltas(
            List<DimensionDetail> current, List<DimensionDetail> simulated) {
        List<SimulateScoreResponse.DimensionDelta> deltas = new ArrayList<>();
        for (DimensionDetail cd : current) {
            DimensionDetail sd = simulated.stream()
                    .filter(d -> d.name().equals(cd.name()))
                    .findFirst().orElse(null);
            BigDecimal simWeighted = sd != null ? sd.weightedScore() : BigDecimal.ZERO;
            deltas.add(SimulateScoreResponse.DimensionDelta.builder()
                    .name(cd.name())
                    .label(cd.label())
                    .currentWeightedScore(cd.weightedScore())
                    .simulatedWeightedScore(simWeighted)
                    .delta(simWeighted.subtract(cd.weightedScore()))
                    .build());
        }
        return deltas;
    }

    /**
     * Simplified monthly payment: principal * (1 + apr/100 * loanDays/365) / (loanDays/30)
     */
    private BigDecimal calcMonthlyPayment(BigDecimal principal, BigDecimal apr, BigDecimal loanDays) {
        // totalRepayment = principal * (1 + apr/100 * loanDays/365)
        BigDecimal aprDecimal = apr.divide(HUNDRED, 10, ROUNDING);
        BigDecimal interestFactor = aprDecimal.multiply(loanDays).divide(THREE_SIXTY_FIVE, 10, ROUNDING);
        BigDecimal totalRepayment = principal.multiply(BigDecimal.ONE.add(interestFactor));
        // months = loanDays / 30
        BigDecimal months = loanDays.divide(DAYS_PER_MONTH, 10, ROUNDING);
        return totalRepayment.divide(months, SCALE, ROUNDING);
    }
}
