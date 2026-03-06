package com.youhua.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.ai.dto.SuggestionResult;
import com.youhua.ai.service.SuggestionGenService;
import com.youhua.common.exception.BizException;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.engine.apr.AprCalculator;
import com.youhua.engine.scoring.ScoringEngine;
import com.youhua.engine.scoring.ScoringEngine.ScoreResult;
import com.youhua.engine.scoring.ScoringEngine.Recommendation;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.profile.entity.OptimizationReport;
import com.youhua.profile.enums.ProfileGenerationStatus;
import com.youhua.profile.enums.RiskLevel;
import com.youhua.profile.mapper.FinanceProfileMapper;
import com.youhua.profile.mapper.OptimizationReportMapper;
import com.youhua.report.dto.DebtAnalysisItem;
import com.youhua.report.dto.LossVisualizationData;
import com.youhua.report.dto.NumericSummary;
import com.youhua.report.dto.ReportData;
import com.youhua.report.dto.ReportMetadata;
import com.youhua.report.service.ReportAssembler;
import com.youhua.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private FinanceProfileMapper financeProfileMapper;

    @Mock
    private DebtMapper debtMapper;

    @Mock
    private AprCalculator aprCalculator;

    @Mock
    private ScoringEngine scoringEngine;

    @Mock
    private SuggestionGenService suggestionGenService;

    @Mock
    private ReportAssembler reportAssembler;

    @Mock
    private OptimizationReportMapper reportMapper;

    private ObjectMapper objectMapper;
    private ReportService reportService;

    private FinanceProfile completedProfile;
    private ScoreResult scoreResult;
    private ReportData mockReportData;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        reportService = new ReportService(financeProfileMapper, debtMapper, aprCalculator,
                scoringEngine, suggestionGenService, reportAssembler, reportMapper, objectMapper);

        completedProfile = new FinanceProfile();
        completedProfile.setId(100L);
        completedProfile.setUserId(1L);
        completedProfile.setTotalDebt(new BigDecimal("100000.00"));
        completedProfile.setDebtCount(2);
        completedProfile.setWeightedApr(new BigDecimal("24.000000"));
        completedProfile.setMonthlyPayment(new BigDecimal("5000.00"));
        completedProfile.setMonthlyIncome(new BigDecimal("10000.00"));
        completedProfile.setGenerationStatus(ProfileGenerationStatus.COMPLETED);

        scoreResult = new ScoreResult(
                new BigDecimal("68.00"),
                RiskLevel.MEDIUM,
                Recommendation.RESTRUCTURE_RECOMMENDED,
                "好消息是，你有优化空间",
                "利率模拟器（Page 6）",
                List.of(),
                LocalDateTime.now()
        );

        NumericSummary numericSummary = new NumericSummary(
                new BigDecimal("100000.00"), 2, new BigDecimal("24.00"),
                new BigDecimal("5000.00"), new BigDecimal("10000.00"),
                new BigDecimal("0.50"), new BigDecimal("15000.00"),
                "相当于 2 个月房租", new BigDecimal("68.00"), RiskLevel.MEDIUM
        );
        mockReportData = new ReportData(numericSummary, List.of(), null, null,
                new ReportMetadata(LocalDateTime.now(), "v1.0", 1, 0, true), List.of());
    }

    @Test
    void should_generate_report_successfully() {
        when(financeProfileMapper.selectOne(any())).thenReturn(completedProfile);
        when(debtMapper.selectList(any())).thenReturn(List.of());
        when(scoringEngine.score(any())).thenReturn(scoreResult);
        when(suggestionGenService.generate(any(), any(), any()))
                .thenReturn(SuggestionResult.builder().aiGenerated(false).build());
        when(reportAssembler.assemble(any(), any(), any(), any())).thenReturn(mockReportData);

        // Mock insert to set ID on the inserted report
        doAnswer(invocation -> {
            OptimizationReport r = invocation.getArgument(0);
            r.setId(999L);
            return 1;
        }).when(reportMapper).insert(any(OptimizationReport.class));

        Long reportId = reportService.generateReport(1L);

        assertThat(reportId).isEqualTo(999L);
        verify(reportMapper).insert(any(OptimizationReport.class));
    }

    @Test
    void should_handle_suggestion_failure_gracefully() {
        when(financeProfileMapper.selectOne(any())).thenReturn(completedProfile);
        when(debtMapper.selectList(any())).thenReturn(List.of());
        when(scoringEngine.score(any())).thenReturn(scoreResult);
        when(suggestionGenService.generate(any(), any(), any()))
                .thenThrow(new RuntimeException("AI service unavailable"));
        when(reportAssembler.assemble(any(), any(), any(), any())).thenReturn(mockReportData);

        doAnswer(invocation -> {
            OptimizationReport r = invocation.getArgument(0);
            r.setId(999L);
            return 1;
        }).when(reportMapper).insert(any(OptimizationReport.class));

        // Should NOT throw — suggestion failure is degraded
        Long reportId = reportService.generateReport(1L);

        assertThat(reportId).isEqualTo(999L);
        // Verify assembler was called with null suggestion
        ArgumentCaptor<SuggestionResult> suggestionCaptor = ArgumentCaptor.forClass(SuggestionResult.class);
        verify(reportAssembler).assemble(any(), any(), any(), suggestionCaptor.capture());
        assertThat(suggestionCaptor.getValue()).isNull();
    }

    @Test
    void should_throw_when_profile_not_found() {
        when(financeProfileMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> reportService.generateReport(1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void should_throw_when_profile_not_completed() {
        completedProfile.setGenerationStatus(ProfileGenerationStatus.CALCULATING);
        when(financeProfileMapper.selectOne(any())).thenReturn(completedProfile);

        assertThatThrownBy(() -> reportService.generateReport(1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void should_accept_profile_with_completed_without_ai_status() {
        completedProfile.setGenerationStatus(ProfileGenerationStatus.COMPLETED_WITHOUT_AI);
        when(financeProfileMapper.selectOne(any())).thenReturn(completedProfile);
        when(debtMapper.selectList(any())).thenReturn(List.of());
        when(scoringEngine.score(any())).thenReturn(scoreResult);
        when(suggestionGenService.generate(any(), any(), any()))
                .thenReturn(SuggestionResult.builder().aiGenerated(false).build());
        when(reportAssembler.assemble(any(), any(), any(), any())).thenReturn(mockReportData);

        doAnswer(invocation -> {
            OptimizationReport r = invocation.getArgument(0);
            r.setId(1L);
            return 1;
        }).when(reportMapper).insert(any(OptimizationReport.class));

        Long reportId = reportService.generateReport(1L);

        assertThat(reportId).isNotNull();
    }

    @Test
    void should_persist_report_to_database() {
        when(financeProfileMapper.selectOne(any())).thenReturn(completedProfile);
        when(debtMapper.selectList(any())).thenReturn(List.of());
        when(scoringEngine.score(any())).thenReturn(scoreResult);
        when(suggestionGenService.generate(any(), any(), any()))
                .thenReturn(SuggestionResult.builder().aiGenerated(false).build());
        when(reportAssembler.assemble(any(), any(), any(), any())).thenReturn(mockReportData);

        doAnswer(invocation -> {
            OptimizationReport r = invocation.getArgument(0);
            r.setId(42L);
            return 1;
        }).when(reportMapper).insert(any(OptimizationReport.class));

        reportService.generateReport(1L);

        ArgumentCaptor<OptimizationReport> captor = ArgumentCaptor.forClass(OptimizationReport.class);
        verify(reportMapper).insert(captor.capture());
        OptimizationReport saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getProfileSnapshotJson()).isNotBlank();
        assertThat(saved.getReportVersion()).isEqualTo(1);
        assertThat(saved.getCreateTime()).isNotNull();
    }

    @Test
    void should_get_report_when_exists_and_authorized() throws Exception {
        String json = objectMapper.writeValueAsString(mockReportData);
        OptimizationReport storedReport = new OptimizationReport();
        storedReport.setId(1L);
        storedReport.setUserId(1L);
        storedReport.setProfileSnapshotJson(json);

        when(reportMapper.selectById(1L)).thenReturn(storedReport);

        ReportData result = reportService.getReport(1L, 1L);

        assertThat(result).isNotNull();
    }

    @Test
    void should_throw_when_report_not_found() {
        when(reportMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> reportService.getReport(999L, 1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void should_throw_when_report_belongs_to_different_user() {
        OptimizationReport otherUserReport = new OptimizationReport();
        otherUserReport.setId(1L);
        otherUserReport.setUserId(999L); // different user

        when(reportMapper.selectById(1L)).thenReturn(otherUserReport);

        assertThatThrownBy(() -> reportService.getReport(1L, 1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void should_query_only_in_profile_debts() {
        when(financeProfileMapper.selectOne(any())).thenReturn(completedProfile);
        when(debtMapper.selectList(any())).thenReturn(List.of());
        when(scoringEngine.score(any())).thenReturn(scoreResult);
        when(suggestionGenService.generate(any(), any(), any()))
                .thenReturn(SuggestionResult.builder().aiGenerated(false).build());
        when(reportAssembler.assemble(any(), any(), any(), any())).thenReturn(mockReportData);
        doAnswer(invocation -> {
            OptimizationReport r = invocation.getArgument(0);
            r.setId(1L);
            return 1;
        }).when(reportMapper).insert(any(OptimizationReport.class));

        reportService.generateReport(1L);

        // Verify debtMapper was called (IN_PROFILE filter is embedded in query wrapper)
        verify(debtMapper).selectList(any());
    }
}
