package com.youhua.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.ai.dto.SuggestionResult;
import com.youhua.ai.service.SuggestionGenService;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.engine.apr.AprCalculator;
import com.youhua.engine.apr.AprCalculator.DebtAprEntry;
import com.youhua.engine.scoring.ScoringEngine;
import com.youhua.engine.scoring.ScoringEngine.ScoreInput;
import com.youhua.engine.scoring.ScoringEngine.ScoreResult;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.profile.entity.OptimizationReport;
import com.youhua.profile.mapper.FinanceProfileMapper;
import com.youhua.profile.enums.ProfileGenerationStatus;
import com.youhua.profile.mapper.OptimizationReportMapper;
import com.youhua.report.dto.ReportData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrates report generation: profile fetch → debt fetch → scoring → AI suggestion → assemble → persist.
 *
 * <p>AI suggestion failure is gracefully degraded: suggestion=null, report still generated (F-02 compliant).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final FinanceProfileMapper financeProfileMapper;
    private final DebtMapper debtMapper;
    private final AprCalculator aprCalculator;
    private final ScoringEngine scoringEngine;
    private final SuggestionGenService suggestionGenService;
    private final ReportAssembler reportAssembler;
    private final OptimizationReportMapper reportMapper;
    private final ObjectMapper objectMapper;

    /**
     * Generates an optimization report for the given user.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load FinanceProfile (must exist and be calculated)</li>
     *   <li>Load IN_PROFILE debts</li>
     *   <li>Run ScoringEngine</li>
     *   <li>Call SuggestionGenService (degraded to null on any failure)</li>
     *   <li>Assemble ReportData via ReportAssembler</li>
     *   <li>Persist to OptimizationReport</li>
     * </ol>
     *
     * @param userId the user's ID
     * @return generated report ID
     * @throws BizException REPORT_PROFILE_INCOMPLETE(406002) when profile is missing or incomplete
     */
    public Long generateReport(Long userId) {
        // Step 1: Load and validate profile
        FinanceProfile profile = loadProfile(userId);

        // Step 2: Load IN_PROFILE debts
        List<Debt> debts = loadInProfileDebts(userId);

        // Step 3: Build ScoreInput and run scoring engine
        ScoreResult scoreResult = runScoring(profile, debts);

        // Step 4: Generate AI suggestion (degraded to null on failure)
        SuggestionResult suggestion = generateSuggestion(profile, debts, scoreResult);

        // Step 5: Assemble report data
        ReportData reportData = reportAssembler.assemble(profile, debts, scoreResult, suggestion);

        // Step 6: Persist
        return persistReport(userId, reportData);
    }

    /**
     * Retrieves a previously generated report.
     *
     * @param reportId report ID
     * @param userId   requesting user ID (authorization check)
     * @return deserialized report data
     * @throws BizException REPORT_NOT_FOUND(406001) when not found or unauthorized
     */
    public ReportData getReport(Long reportId, Long userId) {
        OptimizationReport report = reportMapper.selectById(reportId);
        if (report == null || !userId.equals(report.getUserId())) {
            throw new BizException(ErrorCode.REPORT_NOT_FOUND);
        }

        try {
            return objectMapper.readValue(report.getProfileSnapshotJson(), ReportData.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize report id={}, userId={}", reportId, userId, e);
            throw new BizException(ErrorCode.REPORT_NOT_FOUND, "报告数据解析失败");
        }
    }

    // ===================== Private Helpers =====================

    private String serializeOrEmpty(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize field value, falling back to empty JSON", e);
            return "[]";
        }
    }

    private FinanceProfile loadProfile(Long userId) {
        LambdaQueryWrapper<FinanceProfile> query = new LambdaQueryWrapper<FinanceProfile>()
                .eq(FinanceProfile::getUserId, userId)
                .eq(FinanceProfile::getDeleted, 0)
                .last("LIMIT 1");

        FinanceProfile profile = financeProfileMapper.selectOne(query);

        if (profile == null) {
            throw new BizException(ErrorCode.REPORT_PROFILE_INCOMPLETE, "用户财务画像不存在，请先完成画像计算");
        }
        if (!profile.getGenerationStatus().isTerminal()) {
            throw new BizException(ErrorCode.REPORT_PROFILE_INCOMPLETE,
                    "画像尚未计算完成，当前状态：" + profile.getGenerationStatus());
        }

        return profile;
    }

    private List<Debt> loadInProfileDebts(Long userId) {
        LambdaQueryWrapper<Debt> query = new LambdaQueryWrapper<Debt>()
                .eq(Debt::getUserId, userId)
                .eq(Debt::getStatus, DebtStatus.IN_PROFILE)
                .eq(Debt::getDeleted, 0);

        return debtMapper.selectList(query);
    }

    private ScoreResult runScoring(FinanceProfile profile, List<Debt> debts) {
        // Use profile-stored weighted APR (already calculated); fall back to recalculation if null
        BigDecimal weightedApr = profile.getWeightedApr();
        if (weightedApr == null) {
            List<DebtAprEntry> aprEntries = debts.stream()
                    .filter(d -> d.getPrincipal() != null && d.getApr() != null)
                    .map(d -> new DebtAprEntry(d.getPrincipal(), d.getApr()))
                    .toList();
            weightedApr = aprCalculator.calculateWeightedApr(aprEntries);
        }

        int overdueCount = (int) debts.stream()
                .filter(d -> d.getOverdueStatus() != null
                        && d.getOverdueStatus() != com.youhua.debt.enums.OverdueStatus.NORMAL)
                .count();

        int maxOverdueDays = debts.stream()
                .filter(d -> d.getOverdueDays() != null)
                .mapToInt(Debt::getOverdueDays)
                .max()
                .orElse(0);

        long avgLoanDays = (long) debts.stream()
                .filter(d -> d.getLoanDays() != null)
                .mapToInt(Debt::getLoanDays)
                .average()
                .orElse(0);

        ScoreInput scoreInput = new ScoreInput(
                profile.getMonthlyPayment() != null ? profile.getMonthlyPayment() : BigDecimal.ZERO,
                profile.getMonthlyIncome(),
                weightedApr,
                overdueCount,
                maxOverdueDays,
                debts.size(),
                avgLoanDays
        );

        return scoringEngine.score(scoreInput);
    }

    private SuggestionResult generateSuggestion(FinanceProfile profile, List<Debt> debts, ScoreResult scoreResult) {
        try {
            return suggestionGenService.generate(profile, debts, scoreResult);
        } catch (Exception e) {
            log.warn("AI suggestion generation failed for userId={}, report will proceed without suggestion",
                    profile.getUserId(), e);
            return null;
        }
    }

    private Long persistReport(Long userId, ReportData reportData) {
        String reportJson;
        try {
            reportJson = objectMapper.writeValueAsString(reportData);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ReportData for userId={}", userId, e);
            throw new BizException(ErrorCode.SYSTEM_BUSY, "报告数据序列化失败");
        }

        OptimizationReport report = new OptimizationReport();
        report.setUserId(userId);
        report.setProfileSnapshotJson(reportJson);
        report.setAiSummary(reportData.suggestion() != null ? reportData.suggestion().summary() : null);
        report.setPriorityListJson(serializeOrEmpty(
                reportData.suggestion() != null ? reportData.suggestion().priorityCreditors() : null));
        report.setActionPlanJson(serializeOrEmpty(
                reportData.suggestion() != null ? reportData.suggestion().actionSteps() : null));
        report.setExplainabilityJson(serializeOrEmpty(reportData.numericSummary()));
        report.setRiskWarnings(serializeOrEmpty(reportData.warnings()));
        report.setReportVersion(1);
        report.setCreateTime(LocalDateTime.now());

        reportMapper.insert(report);

        log.debug("Report persisted: reportId={}, userId={}", report.getId(), userId);
        return report.getId();
    }
}
