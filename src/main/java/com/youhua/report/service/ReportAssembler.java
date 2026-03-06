package com.youhua.report.service;

import com.youhua.ai.dto.SuggestionResult;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.engine.apr.AprCalculator;
import com.youhua.engine.scoring.ScoringEngine.ScoreResult;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.report.config.ReportConfigProperties;
import com.youhua.report.dto.AprComparison;
import com.youhua.report.dto.DebtAnalysisItem;
import com.youhua.report.dto.InterestBreakdownItem;
import com.youhua.report.dto.LossVisualizationData;
import com.youhua.report.dto.MonthlyPressure;
import com.youhua.report.dto.NumericSummary;
import com.youhua.report.dto.ReportData;
import com.youhua.report.dto.ReportMetadata;
import com.youhua.report.dto.ReportWarning;
import com.youhua.report.dto.SuggestionLayer;
import com.youhua.report.dto.ThreeYearLoss;
import com.youhua.report.dto.WarningType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Assembles the three-layer explainability report data from raw engine outputs.
 *
 * <p>Layer 1: NumericSummary — quantified debt metrics
 * <p>Layer 2: DebtAnalysis list — per-debt breakdown, sorted by APR descending
 * <p>Layer 3: SuggestionLayer — AI advice (null when degraded)
 *
 * <p>This component is stateless — all data comes from method parameters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportAssembler {

    private static final int INTERMEDIATE_SCALE = 10;
    private static final int FINAL_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Three years expressed in days: 365 * 3 = 1095.
     */
    private static final BigDecimal THREE_YEARS_DAYS = new BigDecimal("1095");

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal OCR_LOW_CONFIDENCE_THRESHOLD = new BigDecimal("70");

    private final AprCalculator aprCalculator;
    private final ReportConfigProperties reportConfig;

    /**
     * Assembles complete report data from engine outputs.
     *
     * @param profile     user's finance profile (already calculated)
     * @param debts       IN_PROFILE debts for the user
     * @param scoreResult result from ScoringEngine
     * @param suggestion  AI suggestion result (may be null on degradation)
     * @return assembled report data
     * @throws BizException REPORT_PROFILE_INCOMPLETE(406002) when profile is missing required fields
     */
    public ReportData assemble(FinanceProfile profile, List<Debt> debts,
                               ScoreResult scoreResult, SuggestionResult suggestion) {
        validateProfile(profile);

        List<Debt> safeDebts = debts != null ? debts : List.of();

        BigDecimal threeYearExtraInterest = calculateThreeYearExtraInterest(safeDebts);
        String analogy = buildRentAnalogy(threeYearExtraInterest);

        NumericSummary numericSummary = buildNumericSummary(
                profile, scoreResult, threeYearExtraInterest, analogy);

        List<DebtAnalysisItem> debtAnalyses = buildDebtAnalyses(safeDebts);

        SuggestionLayer suggestionLayer = buildSuggestionLayer(suggestion);

        LossVisualizationData lossVisualization = buildLossVisualization(
                profile, safeDebts, threeYearExtraInterest, analogy);

        ReportMetadata metadata = buildMetadata(profile, safeDebts);

        List<ReportWarning> warnings = buildWarnings(profile, safeDebts);

        return new ReportData(numericSummary, debtAnalyses, suggestionLayer,
                lossVisualization, metadata, warnings);
    }

    // ===================== Layer 1: Numeric Summary =====================

    private NumericSummary buildNumericSummary(FinanceProfile profile, ScoreResult scoreResult,
                                               BigDecimal threeYearExtraInterest, String analogy) {
        return new NumericSummary(
                profile.getTotalDebt(),
                profile.getDebtCount() != null ? profile.getDebtCount() : 0,
                profile.getWeightedApr(),
                profile.getMonthlyPayment(),
                profile.getMonthlyIncome(),
                profile.getDebtIncomeRatio(),
                threeYearExtraInterest,
                analogy,
                scoreResult.finalScore(),
                scoreResult.riskLevel()
        );
    }

    /**
     * Calculates total extra interest paid over 3 years across all debts.
     *
     * <p>Formula: Σ( (totalRepayment_i - principal_i) / loanDays_i * 1095 )
     * <p>Intermediate steps: scale=10, HALF_UP. Final result: scale=2, HALF_UP.
     * Debts with missing principal or zero loanDays are skipped with a WARN log.
     * This is a pure Java calculation — no AI call (F-02).
     */
    public BigDecimal calculateThreeYearExtraInterest(List<Debt> debts) {
        BigDecimal total = BigDecimal.ZERO;

        for (Debt debt : debts) {
            if (debt.getPrincipal() == null) {
                log.warn("Skipping debt id={} in threeYearExtraInterest: principal is null", debt.getId());
                continue;
            }
            if (debt.getTotalRepayment() == null) {
                log.warn("Skipping debt id={} in threeYearExtraInterest: totalRepayment is null", debt.getId());
                continue;
            }
            if (debt.getLoanDays() == null || debt.getLoanDays() == 0) {
                log.warn("Skipping debt id={} in threeYearExtraInterest: loanDays is null or zero", debt.getId());
                continue;
            }

            BigDecimal totalInterest = debt.getTotalRepayment().subtract(debt.getPrincipal());
            if (totalInterest.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Skipping debt id={} in threeYearExtraInterest: totalInterest < 0", debt.getId());
                continue;
            }

            BigDecimal loanDaysBd = new BigDecimal(debt.getLoanDays());
            BigDecimal dailyInterest = totalInterest.divide(loanDaysBd, INTERMEDIATE_SCALE, ROUNDING);
            BigDecimal extraInterest = dailyInterest.multiply(THREE_YEARS_DAYS)
                    .setScale(INTERMEDIATE_SCALE, ROUNDING);

            log.debug("Debt id={}: totalInterest={}, dailyInterest={}, extraInterest={}",
                    debt.getId(), totalInterest, dailyInterest, extraInterest);

            total = total.add(extraInterest);
        }

        return total.setScale(FINAL_SCALE, ROUNDING);
    }

    /**
     * Builds the rent analogy string. Returns null if avgMonthlyRent is unconfigured or zero.
     */
    public String buildRentAnalogy(BigDecimal threeYearExtraInterest) {
        BigDecimal avgMonthlyRent = reportConfig.getAvgMonthlyRent();
        if (avgMonthlyRent == null || avgMonthlyRent.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal months = threeYearExtraInterest.divide(avgMonthlyRent, 0, ROUNDING);
        return "相当于 " + months.toPlainString() + " 个月房租";
    }

    // ===================== Layer 2: Debt Analysis =====================

    private List<DebtAnalysisItem> buildDebtAnalyses(List<Debt> debts) {
        BigDecimal totalInterestAll = debts.stream()
                .filter(d -> d.getTotalRepayment() != null && d.getPrincipal() != null)
                .map(d -> d.getTotalRepayment().subtract(d.getPrincipal()))
                .filter(i -> i.compareTo(BigDecimal.ZERO) >= 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<DebtAnalysisItem> items = new ArrayList<>();
        for (Debt debt : debts) {
            BigDecimal totalInterest = BigDecimal.ZERO;
            if (debt.getTotalRepayment() != null && debt.getPrincipal() != null) {
                BigDecimal raw = debt.getTotalRepayment().subtract(debt.getPrincipal());
                totalInterest = raw.compareTo(BigDecimal.ZERO) >= 0
                        ? raw.setScale(FINAL_SCALE, ROUNDING)
                        : BigDecimal.ZERO.setScale(FINAL_SCALE, ROUNDING);
            }

            BigDecimal interestContribution = calculateInterestContribution(totalInterest, totalInterestAll);

            items.add(new DebtAnalysisItem(
                    debt.getId(),
                    debt.getCreditor(),
                    debt.getDebtType(),
                    debt.getPrincipal(),
                    debt.getApr(),
                    debt.getMonthlyPayment(),
                    totalInterest,
                    interestContribution,
                    debt.getApr() != null ? aprCalculator.getAprLevel(debt.getApr()) : null,
                    debt.getSourceType(),
                    debt.getOverdueStatus()
            ));
        }

        // Sort by APR descending (nulls last)
        items.sort(Comparator.comparing(
                DebtAnalysisItem::apr,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        return items;
    }

    public BigDecimal calculateInterestContribution(BigDecimal debtInterest, BigDecimal totalInterestAll) {
        if (totalInterestAll.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(FINAL_SCALE, ROUNDING);
        }
        return debtInterest
                .multiply(HUNDRED)
                .divide(totalInterestAll, FINAL_SCALE, ROUNDING);
    }

    // ===================== Layer 3: Suggestion =====================

    private SuggestionLayer buildSuggestionLayer(SuggestionResult suggestion) {
        if (suggestion == null) {
            return null;
        }
        return new SuggestionLayer(
                suggestion.getEmpathy(),
                suggestion.getQuantifiedLoss(),
                suggestion.getPositiveTurn(),
                suggestion.getActionSteps(),
                suggestion.getSafetyClosure(),
                suggestion.getSummary(),
                suggestion.getPriorityCreditors(),
                suggestion.isAiGenerated()
        );
    }

    // ===================== Loss Visualization =====================

    private LossVisualizationData buildLossVisualization(FinanceProfile profile, List<Debt> debts,
                                                          BigDecimal threeYearExtraInterest, String analogy) {
        ThreeYearLoss threeYearLoss = buildThreeYearLoss(threeYearExtraInterest, analogy);
        AprComparison aprComparison = buildAprComparison(profile);
        MonthlyPressure monthlyPressure = buildMonthlyPressure(profile);
        List<InterestBreakdownItem> interestBreakdown = buildInterestBreakdown(debts);

        return new LossVisualizationData(threeYearLoss, aprComparison, monthlyPressure, interestBreakdown);
    }

    private ThreeYearLoss buildThreeYearLoss(BigDecimal threeYearExtraInterest, String analogy) {
        String displayFormat = "如果维持当前结构，3 年将多支付 "
                + threeYearExtraInterest.toPlainString() + " 元";
        return new ThreeYearLoss(threeYearExtraInterest, analogy, displayFormat);
    }

    private AprComparison buildAprComparison(FinanceProfile profile) {
        BigDecimal currentApr = profile.getWeightedApr() != null
                ? profile.getWeightedApr().setScale(FINAL_SCALE, ROUNDING)
                : BigDecimal.ZERO.setScale(FINAL_SCALE, ROUNDING);
        BigDecimal marketAvg = reportConfig.getMarketAvgApr().setScale(FINAL_SCALE, ROUNDING);
        BigDecimal gap = currentApr.subtract(marketAvg).setScale(FINAL_SCALE, ROUNDING);

        String displayFormat;
        if (gap.compareTo(BigDecimal.ZERO) <= 0) {
            displayFormat = "你的综合利率低于市场均值，表现良好";
        } else {
            displayFormat = "你的综合利率 " + currentApr.toPlainString()
                    + "%，市场均值 " + marketAvg.toPlainString() + "%";
        }

        return new AprComparison(currentApr, marketAvg, gap, displayFormat);
    }

    private MonthlyPressure buildMonthlyPressure(FinanceProfile profile) {
        BigDecimal healthyLine = reportConfig.getHealthyDebtIncomeRatio().setScale(FINAL_SCALE, ROUNDING);

        if (profile.getMonthlyIncome() == null
                || profile.getMonthlyIncome().compareTo(BigDecimal.ZERO) == 0) {
            return new MonthlyPressure(null, healthyLine, false, "填写收入获取更精确分析");
        }

        BigDecimal ratio = profile.getMonthlyPayment()
                .divide(profile.getMonthlyIncome(), FINAL_SCALE, ROUNDING);
        String displayFormat = "月供占收入 " + ratio.multiply(HUNDRED).setScale(FINAL_SCALE, ROUNDING).toPlainString()
                + "%，健康线为 "
                + healthyLine.multiply(HUNDRED).setScale(FINAL_SCALE, ROUNDING).toPlainString() + "% 以下";

        return new MonthlyPressure(ratio, healthyLine, true, displayFormat);
    }

    private List<InterestBreakdownItem> buildInterestBreakdown(List<Debt> debts) {
        List<Debt> validDebts = debts.stream()
                .filter(d -> d.getTotalRepayment() != null && d.getPrincipal() != null)
                .filter(d -> d.getTotalRepayment().subtract(d.getPrincipal()).compareTo(BigDecimal.ZERO) >= 0)
                .toList();

        BigDecimal totalInterestAll = validDebts.stream()
                .map(d -> d.getTotalRepayment().subtract(d.getPrincipal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<InterestBreakdownItem> items = new ArrayList<>();
        BigDecimal runningPercentage = BigDecimal.ZERO;

        // Sort by interestAmount descending for pie chart
        List<Debt> sortedDebts = new ArrayList<>(validDebts);
        sortedDebts.sort(Comparator.comparing(
                d -> d.getTotalRepayment().subtract(d.getPrincipal()),
                Comparator.reverseOrder()
        ));

        for (int i = 0; i < sortedDebts.size(); i++) {
            Debt debt = sortedDebts.get(i);
            BigDecimal interestAmount = debt.getTotalRepayment().subtract(debt.getPrincipal())
                    .setScale(FINAL_SCALE, ROUNDING);

            BigDecimal percentage;
            if (i == sortedDebts.size() - 1 && !items.isEmpty()) {
                // Last item: use remainder to ensure percentages sum to exactly 100
                percentage = HUNDRED.subtract(runningPercentage).setScale(FINAL_SCALE, ROUNDING);
            } else {
                percentage = calculateInterestContribution(interestAmount, totalInterestAll);
            }
            runningPercentage = runningPercentage.add(percentage);

            items.add(new InterestBreakdownItem(
                    debt.getId(),
                    debt.getCreditor(),
                    interestAmount,
                    percentage
            ));
        }

        return items;
    }

    // ===================== Metadata & Warnings =====================

    private ReportMetadata buildMetadata(FinanceProfile profile, List<Debt> debts) {
        int manualCount = (int) debts.stream()
                .filter(d -> DebtSourceType.MANUAL == d.getSourceType())
                .count();
        int ocrCount = (int) debts.stream()
                .filter(d -> DebtSourceType.OCR == d.getSourceType())
                .count();

        return new ReportMetadata(
                LocalDateTime.now(),
                reportConfig.getScoringModelVersion(),
                manualCount,
                ocrCount,
                profile.getMonthlyIncome() != null
        );
    }

    private List<ReportWarning> buildWarnings(FinanceProfile profile, List<Debt> debts) {
        List<ReportWarning> warnings = new ArrayList<>();

        if (profile.getMonthlyIncome() == null) {
            warnings.add(new ReportWarning(
                    WarningType.INCOME_MISSING,
                    "您尚未填写收入信息，负债收入比和部分建议基于估算值"
            ));
        }

        boolean hasLowConfidenceOcr = debts.stream().anyMatch(d ->
                DebtSourceType.OCR == d.getSourceType()
                        && d.getConfidenceScore() != null
                        && d.getConfidenceScore().compareTo(OCR_LOW_CONFIDENCE_THRESHOLD) < 0
        );
        if (hasLowConfidenceOcr) {
            warnings.add(new ReportWarning(
                    WarningType.OCR_LOW_CONFIDENCE,
                    "部分债务数据由AI识别，置信度偏低，建议人工核对后再查看报告"
            ));
        }

        long draftCount = debts.stream()
                .filter(d -> d.getStatus() == DebtStatus.DRAFT
                        || d.getStatus() == DebtStatus.PENDING_CONFIRM)
                .count();
        if (draftCount > 0) {
            warnings.add(new ReportWarning(
                    WarningType.DRAFT_PENDING,
                    "您有 " + draftCount + " 笔债务尚未确认，确认后报告将更准确"
            ));
        }

        return warnings;
    }

    // ===================== Validation =====================

    private void validateProfile(FinanceProfile profile) {
        if (profile == null) {
            throw new BizException(ErrorCode.REPORT_PROFILE_INCOMPLETE, "画像数据不能为空");
        }
        if (profile.getTotalDebt() == null) {
            throw new BizException(ErrorCode.REPORT_PROFILE_INCOMPLETE, "画像数据不完整：缺少总负债");
        }
        if (profile.getWeightedApr() == null) {
            throw new BizException(ErrorCode.REPORT_PROFILE_INCOMPLETE, "画像数据不完整：缺少加权年化利率");
        }
        if (profile.getMonthlyPayment() == null) {
            throw new BizException(ErrorCode.REPORT_PROFILE_INCOMPLETE, "画像数据不完整：缺少月供总额");
        }
    }
}
