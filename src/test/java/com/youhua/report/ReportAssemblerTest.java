package com.youhua.report;

import com.youhua.ai.dto.SuggestionResult;
import com.youhua.common.exception.BizException;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.enums.DebtType;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.engine.apr.AprCalculator;
import com.youhua.engine.apr.AprConfig;
import com.youhua.engine.apr.AprLevel;
import com.youhua.engine.scoring.ScoringEngine.ScoreResult;
import com.youhua.engine.scoring.ScoringEngine.DimensionDetail;
import com.youhua.engine.scoring.ScoringEngine.Recommendation;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.profile.enums.RiskLevel;
import com.youhua.report.config.ReportConfigProperties;
import com.youhua.report.dto.DebtAnalysisItem;
import com.youhua.report.dto.ReportData;
import com.youhua.report.dto.ReportWarning;
import com.youhua.report.dto.WarningType;
import com.youhua.report.service.ReportAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportAssemblerTest {

    @Mock
    private AprCalculator aprCalculator;

    private ReportConfigProperties reportConfig;
    private ReportAssembler assembler;

    private FinanceProfile profile;
    private ScoreResult scoreResult;

    @BeforeEach
    void setUp() {
        reportConfig = new ReportConfigProperties();
        reportConfig.setMarketAvgApr(new BigDecimal("8.5"));
        reportConfig.setAvgMonthlyRent(new BigDecimal("6000.00"));
        reportConfig.setHealthyDebtIncomeRatio(new BigDecimal("0.40"));
        reportConfig.setScoringModelVersion("v1.0");

        assembler = new ReportAssembler(aprCalculator, reportConfig);

        profile = new FinanceProfile();
        profile.setUserId(1L);
        profile.setTotalDebt(new BigDecimal("100000.00"));
        profile.setDebtCount(2);
        profile.setWeightedApr(new BigDecimal("24.000000"));
        profile.setMonthlyPayment(new BigDecimal("5000.00"));
        profile.setMonthlyIncome(new BigDecimal("10000.00"));
        profile.setDebtIncomeRatio(new BigDecimal("0.50"));

        scoreResult = new ScoreResult(
                new BigDecimal("68.00"),
                RiskLevel.MEDIUM,
                Recommendation.RESTRUCTURE_RECOMMENDED,
                "好消息是，你有优化空间",
                "利率模拟器（Page 6）",
                List.of(),
                LocalDateTime.now()
        );
    }

    @Test
    void should_assemble_complete_report_when_all_data_present() {
        List<Debt> debts = List.of(buildDebt(1L, "平安普惠", "10000.00", "15000.00", 365,
                "24.0", DebtSourceType.MANUAL, OverdueStatus.NORMAL, 90.0));
        when(aprCalculator.getAprLevel(any())).thenReturn(AprLevel.WARNING);

        ReportData report = assembler.assemble(profile, debts, scoreResult, null);

        assertThat(report).isNotNull();
        assertThat(report.numericSummary()).isNotNull();
        assertThat(report.debtAnalyses()).hasSize(1);
        assertThat(report.suggestion()).isNull();
        assertThat(report.lossVisualization()).isNotNull();
        assertThat(report.metadata()).isNotNull();
        assertThat(report.warnings()).isNotNull();
    }

    @Test
    void should_calculate_three_year_extra_interest_correctly() {
        // debt: totalRepayment=15000, principal=10000, loanDays=365
        // dailyInterest = (15000-10000)/365 = 5000/365
        // extraInterest = dailyInterest * 1095 = 5000/365 * 1095 = 5000*3 = 15000
        Debt debt = buildDebt(1L, "A银行", "10000.00", "15000.00", 365,
                "50.0", DebtSourceType.MANUAL, OverdueStatus.NORMAL, 90.0);

        BigDecimal result = assembler.calculateThreeYearExtraInterest(List.of(debt));

        assertThat(result).isEqualByComparingTo(new BigDecimal("15000.00"));
    }

    @Test
    void should_calculate_three_year_extra_interest_for_multiple_debts() {
        // debt1: (15000-10000)/365 * 1095 = 15000
        // debt2: (12000-10000)/365 * 1095 = 6000
        // total = 21000
        Debt debt1 = buildDebt(1L, "A银行", "10000.00", "15000.00", 365,
                "50.0", DebtSourceType.MANUAL, OverdueStatus.NORMAL, 90.0);
        Debt debt2 = buildDebt(2L, "B银行", "10000.00", "12000.00", 365,
                "20.0", DebtSourceType.MANUAL, OverdueStatus.NORMAL, 90.0);

        BigDecimal result = assembler.calculateThreeYearExtraInterest(List.of(debt1, debt2));

        assertThat(result).isEqualByComparingTo(new BigDecimal("21000.00"));
    }

    @Test
    void should_skip_debt_when_loan_days_is_zero() {
        Debt valid = buildDebt(1L, "A银行", "10000.00", "15000.00", 365,
                "50.0", DebtSourceType.MANUAL, OverdueStatus.NORMAL, 90.0);

        Debt invalid = buildDebt(2L, "B银行", "10000.00", "12000.00", 0,
                "20.0", DebtSourceType.MANUAL, OverdueStatus.NORMAL, 90.0);

        BigDecimal result = assembler.calculateThreeYearExtraInterest(List.of(valid, invalid));

        // Only valid debt contributes: 15000
        assertThat(result).isEqualByComparingTo(new BigDecimal("15000.00"));
    }

    @Test
    void should_skip_debt_when_principal_is_null() {
        Debt debtWithNullPrincipal = new Debt();
        debtWithNullPrincipal.setId(1L);
        debtWithNullPrincipal.setTotalRepayment(new BigDecimal("15000.00"));
        debtWithNullPrincipal.setLoanDays(365);

        BigDecimal result = assembler.calculateThreeYearExtraInterest(List.of(debtWithNullPrincipal));

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_calculate_interest_contribution_correctly() {
        // debt interest = 5000, total interest = 10000 → 50.00%
        BigDecimal contribution = assembler.calculateInterestContribution(
                new BigDecimal("5000"), new BigDecimal("10000"));

        assertThat(contribution).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void should_return_zero_contribution_when_total_interest_is_zero() {
        BigDecimal contribution = assembler.calculateInterestContribution(
                new BigDecimal("5000"), BigDecimal.ZERO);

        assertThat(contribution).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_generate_analogy_when_avg_rent_configured() {
        // threeYearExtraInterest = 18000, avgMonthlyRent = 6000 → 3 months
        BigDecimal threeYearInterest = new BigDecimal("18000.00");

        String analogy = assembler.buildRentAnalogy(threeYearInterest);

        assertThat(analogy).isEqualTo("相当于 3 个月房租");
    }

    @Test
    void should_skip_analogy_when_avg_rent_zero() {
        reportConfig.setAvgMonthlyRent(BigDecimal.ZERO);

        String analogy = assembler.buildRentAnalogy(new BigDecimal("18000.00"));

        assertThat(analogy).isNull();
    }

    @Test
    void should_skip_analogy_when_avg_rent_null() {
        reportConfig.setAvgMonthlyRent(null);

        String analogy = assembler.buildRentAnalogy(new BigDecimal("18000.00"));

        assertThat(analogy).isNull();
    }

    @Test
    void should_add_income_missing_warning_when_no_income() {
        profile.setMonthlyIncome(null);
        List<Debt> debts = List.of();

        ReportData report = assembler.assemble(profile, debts, scoreResult, null);

        List<ReportWarning> warnings = report.warnings();
        assertThat(warnings).anyMatch(w -> w.type() == WarningType.INCOME_MISSING);
    }

    @Test
    void should_add_ocr_low_confidence_warning() {
        Debt ocrDebt = buildDebt(1L, "平安普惠", "10000.00", "15000.00", 365,
                "24.0", DebtSourceType.OCR, OverdueStatus.NORMAL, 60.0); // confidence < 70

        when(aprCalculator.getAprLevel(any())).thenReturn(AprLevel.NORMAL);

        ReportData report = assembler.assemble(profile, List.of(ocrDebt), scoreResult, null);

        assertThat(report.warnings()).anyMatch(w -> w.type() == WarningType.OCR_LOW_CONFIDENCE);
    }

    @Test
    void should_not_add_ocr_warning_when_confidence_is_high() {
        Debt ocrDebt = buildDebt(1L, "平安普惠", "10000.00", "15000.00", 365,
                "24.0", DebtSourceType.OCR, OverdueStatus.NORMAL, 85.0); // confidence >= 70

        when(aprCalculator.getAprLevel(any())).thenReturn(AprLevel.NORMAL);

        ReportData report = assembler.assemble(profile, List.of(ocrDebt), scoreResult, null);

        assertThat(report.warnings()).noneMatch(w -> w.type() == WarningType.OCR_LOW_CONFIDENCE);
    }

    @Test
    void should_handle_null_suggestion_gracefully() {
        List<Debt> debts = List.of();

        ReportData report = assembler.assemble(profile, debts, scoreResult, null);

        assertThat(report.suggestion()).isNull();
    }

    @Test
    void should_populate_suggestion_layer_when_suggestion_present() {
        SuggestionResult suggestion = SuggestionResult.builder()
                .empathy("管理债务确实不容易")
                .quantifiedLoss("3年多付15000元")
                .positiveTurn("有优化空间")
                .actionSteps(List.of("步骤1", "步骤2"))
                .safetyClosure("随时可以调整节奏")
                .summary("总结内容")
                .priorityCreditors(List.of("平安普惠"))
                .aiGenerated(true)
                .build();

        ReportData report = assembler.assemble(profile, List.of(), scoreResult, suggestion);

        assertThat(report.suggestion()).isNotNull();
        assertThat(report.suggestion().empathy()).isEqualTo("管理债务确实不容易");
        assertThat(report.suggestion().aiGenerated()).isTrue();
    }

    @Test
    void should_set_monthly_pressure_not_displayed_when_no_income() {
        profile.setMonthlyIncome(null);

        ReportData report = assembler.assemble(profile, List.of(), scoreResult, null);

        assertThat(report.lossVisualization().monthlyPressure().displayed()).isFalse();
        assertThat(report.lossVisualization().monthlyPressure().displayFormat())
                .isEqualTo("填写收入获取更精确分析");
    }

    @Test
    void should_set_monthly_pressure_displayed_when_income_present() {
        // monthlyPayment=5000, monthlyIncome=10000 → ratio=0.50
        ReportData report = assembler.assemble(profile, List.of(), scoreResult, null);

        assertThat(report.lossVisualization().monthlyPressure().displayed()).isTrue();
        assertThat(report.lossVisualization().monthlyPressure().ratio())
                .isEqualByComparingTo(new BigDecimal("0.50"));
    }

    @Test
    void should_calculate_gap_correctly_when_user_below_market_avg() {
        // user APR = 6%, market = 8.5% → gap = -2.5 (below market, good)
        profile.setWeightedApr(new BigDecimal("6.000000"));

        ReportData report = assembler.assemble(profile, List.of(), scoreResult, null);

        assertThat(report.lossVisualization().currentVsHealthy().gap())
                .isEqualByComparingTo(new BigDecimal("-2.50"));
        assertThat(report.lossVisualization().currentVsHealthy().displayFormat())
                .contains("低于市场均值");
    }

    @Test
    void should_sort_debt_analyses_by_apr_descending() {
        when(aprCalculator.getAprLevel(any())).thenReturn(AprLevel.NORMAL);

        Debt lowApr = buildDebt(1L, "低利率银行", "10000.00", "12000.00", 365,
                "10.0", DebtSourceType.MANUAL, OverdueStatus.NORMAL, 90.0);
        Debt highApr = buildDebt(2L, "高利率机构", "10000.00", "18000.00", 365,
                "36.0", DebtSourceType.MANUAL, OverdueStatus.NORMAL, 90.0);

        ReportData report = assembler.assemble(profile, List.of(lowApr, highApr), scoreResult, null);

        List<DebtAnalysisItem> analyses = report.debtAnalyses();
        assertThat(analyses.get(0).apr()).isEqualByComparingTo(new BigDecimal("36.0"));
        assertThat(analyses.get(1).apr()).isEqualByComparingTo(new BigDecimal("10.0"));
    }

    @Test
    void should_throw_when_profile_is_null() {
        assertThatThrownBy(() -> assembler.assemble(null, List.of(), scoreResult, null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void should_throw_when_profile_missing_total_debt() {
        profile.setTotalDebt(null);

        assertThatThrownBy(() -> assembler.assemble(profile, List.of(), scoreResult, null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void should_throw_when_profile_missing_monthly_payment() {
        profile.setMonthlyPayment(null);

        assertThatThrownBy(() -> assembler.assemble(profile, List.of(), scoreResult, null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void should_return_zero_three_year_interest_when_no_debts() {
        BigDecimal result = assembler.calculateThreeYearExtraInterest(List.of());

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_count_manual_and_ocr_debts_in_metadata() {
        when(aprCalculator.getAprLevel(any())).thenReturn(AprLevel.NORMAL);

        Debt manual1 = buildDebt(1L, "A", "10000.00", "12000.00", 365,
                "20.0", DebtSourceType.MANUAL, OverdueStatus.NORMAL, 90.0);
        Debt manual2 = buildDebt(2L, "B", "10000.00", "12000.00", 365,
                "20.0", DebtSourceType.MANUAL, OverdueStatus.NORMAL, 90.0);
        Debt ocr1 = buildDebt(3L, "C", "10000.00", "12000.00", 365,
                "20.0", DebtSourceType.OCR, OverdueStatus.NORMAL, 90.0);

        ReportData report = assembler.assemble(profile, List.of(manual1, manual2, ocr1), scoreResult, null);

        assertThat(report.metadata().manualCount()).isEqualTo(2);
        assertThat(report.metadata().ocrCount()).isEqualTo(1);
        assertThat(report.metadata().incomeProvided()).isTrue();
    }

    @Test
    void should_include_scoring_model_version_in_metadata() {
        ReportData report = assembler.assemble(profile, List.of(), scoreResult, null);

        assertThat(report.metadata().scoringModelVersion()).isEqualTo("v1.0");
    }

    // ===================== Helpers =====================

    private Debt buildDebt(Long id, String creditor, String principal, String totalRepayment,
                            int loanDays, String apr, DebtSourceType sourceType,
                            OverdueStatus overdueStatus, double confidence) {
        Debt debt = new Debt();
        debt.setId(id);
        debt.setCreditor(creditor);
        debt.setPrincipal(new BigDecimal(principal));
        debt.setTotalRepayment(new BigDecimal(totalRepayment));
        debt.setLoanDays(loanDays);
        debt.setApr(new BigDecimal(apr));
        debt.setMonthlyPayment(new BigDecimal(principal).divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP));
        debt.setSourceType(sourceType);
        debt.setOverdueStatus(overdueStatus);
        debt.setConfidenceScore(BigDecimal.valueOf(confidence));
        debt.setDebtType(DebtType.CONSUMER_LOAN);
        debt.setStatus(DebtStatus.IN_PROFILE);
        return debt;
    }
}
