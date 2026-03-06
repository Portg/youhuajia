package com.youhua.profile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.profile.dto.response.ListReportsResponse;
import com.youhua.profile.dto.response.ReportResponse;
import com.youhua.profile.entity.OptimizationReport;
import com.youhua.profile.mapper.OptimizationReportMapper;
import com.youhua.profile.service.impl.ReportServiceImpl;
import com.youhua.report.dto.NumericSummary;
import com.youhua.report.dto.ReportData;
import com.youhua.report.service.PdfExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ReportServiceImpl Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceImplTest {

    @Mock private com.youhua.report.service.ReportService reportGenerationService;
    @Mock private PdfExportService pdfExportService;
    @Mock private OptimizationReportMapper reportMapper;

    private ReportServiceImpl service;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_REPORT_ID = 500001L;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(
                reportGenerationService, pdfExportService, reportMapper,
                new ObjectMapper()
        );
        // Set up request context
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", TEST_USER_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // ===================== generateReport() tests =====================

    @Test
    @DisplayName("should_generateAndReturnReport_when_profileIsComplete")
    void should_generateAndReturnReport_when_profileIsComplete() {
        OptimizationReport report = buildReport(TEST_REPORT_ID, TEST_USER_ID);
        when(reportGenerationService.generateReport(TEST_USER_ID)).thenReturn(TEST_REPORT_ID);
        when(reportMapper.selectById(TEST_REPORT_ID)).thenReturn(report);

        ReportResponse response = service.generateReport();

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("reports/" + TEST_REPORT_ID);
        assertThat(response.getAiSummary()).isEqualTo("AI 建议摘要");
        verify(reportGenerationService).generateReport(TEST_USER_ID);
    }

    @Test
    @DisplayName("should_throwBizException_when_generatedReportNotFound")
    void should_throwBizException_when_generatedReportNotFound() {
        when(reportGenerationService.generateReport(TEST_USER_ID)).thenReturn(TEST_REPORT_ID);
        when(reportMapper.selectById(TEST_REPORT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.generateReport())
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("should_throwBizException_when_profileIncomplete")
    void should_throwBizException_when_profileIncomplete() {
        when(reportGenerationService.generateReport(TEST_USER_ID))
                .thenThrow(new BizException(ErrorCode.REPORT_PROFILE_INCOMPLETE, "画像未完成"));

        assertThatThrownBy(() -> service.generateReport())
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_PROFILE_INCOMPLETE);
    }

    // ===================== getReport() tests =====================

    @Test
    @DisplayName("should_returnReport_when_reportExistsAndBelongsToUser")
    void should_returnReport_when_reportExistsAndBelongsToUser() {
        OptimizationReport report = buildReport(TEST_REPORT_ID, TEST_USER_ID);
        when(reportMapper.selectById(TEST_REPORT_ID)).thenReturn(report);

        ReportResponse response = service.getReport(TEST_REPORT_ID);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("reports/" + TEST_REPORT_ID);
    }

    @Test
    @DisplayName("should_throwBizException_when_reportNotFound")
    void should_throwBizException_when_reportNotFound() {
        when(reportMapper.selectById(TEST_REPORT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getReport(TEST_REPORT_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("should_throwBizException_when_reportBelongsToDifferentUser")
    void should_throwBizException_when_reportBelongsToDifferentUser() {
        Long otherUserId = 999L;
        OptimizationReport report = buildReport(TEST_REPORT_ID, otherUserId);
        when(reportMapper.selectById(TEST_REPORT_ID)).thenReturn(report);

        assertThatThrownBy(() -> service.getReport(TEST_REPORT_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    // ===================== exportReport() tests =====================

    @Test
    @DisplayName("should_returnPdfBytes_when_reportExistsWithValidJson")
    void should_returnPdfBytes_when_reportExistsWithValidJson() throws Exception {
        ReportData reportData = buildReportData();
        String reportJson = new ObjectMapper().writeValueAsString(reportData);
        OptimizationReport report = buildReport(TEST_REPORT_ID, TEST_USER_ID);
        report.setProfileSnapshotJson(reportJson);

        byte[] mockPdf = "PDF_BYTES".getBytes();
        when(reportMapper.selectById(TEST_REPORT_ID)).thenReturn(report);
        when(pdfExportService.export(any())).thenReturn(mockPdf);

        byte[] result = service.exportReport(TEST_REPORT_ID);

        assertThat(result).isEqualTo(mockPdf);
        verify(pdfExportService).export(any());
    }

    @Test
    @DisplayName("should_throwBizException_when_exportReportNotFound")
    void should_throwBizException_when_exportReportNotFound() {
        when(reportMapper.selectById(TEST_REPORT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.exportReport(TEST_REPORT_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    // ===================== listReports() tests =====================

    @Test
    @DisplayName("should_returnReportList_when_reportsExist")
    void should_returnReportList_when_reportsExist() {
        List<OptimizationReport> reports = List.of(
                buildReport(500003L, TEST_USER_ID),
                buildReport(500002L, TEST_USER_ID),
                buildReport(500001L, TEST_USER_ID)
        );
        when(reportMapper.selectList(any())).thenReturn(reports);

        ListReportsResponse response = service.listReports(10, null, "createTime desc");

        assertThat(response).isNotNull();
        assertThat(response.getReports()).hasSize(3);
        assertThat(response.getNextPageToken()).isNull();
    }

    @Test
    @DisplayName("should_returnNextPageToken_when_moreReportsExist")
    void should_returnNextPageToken_when_moreReportsExist() {
        // Return pageSize+1 records to signal there's a next page
        List<OptimizationReport> reports = List.of(
                buildReport(500003L, TEST_USER_ID),
                buildReport(500002L, TEST_USER_ID),
                buildReport(500001L, TEST_USER_ID)
        );
        when(reportMapper.selectList(any())).thenReturn(reports);

        ListReportsResponse response = service.listReports(2, null, "createTime desc");

        assertThat(response.getReports()).hasSize(2);
        assertThat(response.getNextPageToken()).isNotNull();
        String expectedToken = Base64.getEncoder().encodeToString(String.valueOf(500002L).getBytes());
        assertThat(response.getNextPageToken()).isEqualTo(expectedToken);
    }

    @Test
    @DisplayName("should_returnEmptyList_when_noReportsExist")
    void should_returnEmptyList_when_noReportsExist() {
        when(reportMapper.selectList(any())).thenReturn(Collections.emptyList());

        ListReportsResponse response = service.listReports(10, null, "createTime desc");

        assertThat(response.getReports()).isEmpty();
        assertThat(response.getNextPageToken()).isNull();
    }

    // ===================== Helpers =====================

    private OptimizationReport buildReport(Long id, Long userId) {
        OptimizationReport report = new OptimizationReport();
        report.setId(id);
        report.setUserId(userId);
        report.setProfileSnapshotJson("{}");
        report.setAiSummary("AI 建议摘要");
        report.setReportVersion(1);
        report.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
        report.setDeleted(0);
        return report;
    }

    private ReportData buildReportData() {
        NumericSummary summary = new NumericSummary(
                new BigDecimal("50000"), 3,
                new BigDecimal("15.000000"),
                new BigDecimal("3000"),
                new BigDecimal("15000"),
                new BigDecimal("0.2"),
                new BigDecimal("5000"),
                "相当于5个月房租",
                new BigDecimal("75.00"),
                com.youhua.profile.enums.RiskLevel.MEDIUM
        );
        return new ReportData(summary, List.of(), null, null, null, List.of());
    }
}
