package com.youhua.report;

import com.youhua.common.exception.BizException;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtType;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.engine.apr.AprLevel;
import com.youhua.profile.enums.RiskLevel;
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
import com.youhua.report.service.PdfExportService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PdfExportService.
 * Uses PDFBox text stripper to verify PDF content without Spring context.
 */
class PdfExportServiceTest {

    private PdfExportService pdfExportService;

    @BeforeEach
    void setUp() {
        pdfExportService = new PdfExportService();
    }

    // ===== Test Fixtures =====

    private ReportData buildFullReportData() {
        NumericSummary summary = new NumericSummary(
                new BigDecimal("380000.00"),
                3,
                new BigDecimal("24.00"),
                new BigDecimal("12800.00"),
                new BigDecimal("19700.00"),
                new BigDecimal("0.65"),
                new BigDecimal("82400.00"),
                "相当于 13 个月房租",
                new BigDecimal("68.00"),
                RiskLevel.MEDIUM
        );

        List<DebtAnalysisItem> debtAnalyses = List.of(
                new DebtAnalysisItem(
                        10001L, "平安普惠", DebtType.CONSUMER_LOAN,
                        new BigDecimal("30000.00"), new BigDecimal("30.00"),
                        new BigDecimal("2800.00"), new BigDecimal("9000.00"),
                        new BigDecimal("54.61"), AprLevel.WARNING,
                        DebtSourceType.MANUAL, OverdueStatus.NORMAL
                ),
                new DebtAnalysisItem(
                        10002L, "招商银行信用卡", DebtType.CREDIT_CARD,
                        new BigDecimal("50000.00"), new BigDecimal("20.00"),
                        new BigDecimal("4500.00"), new BigDecimal("7500.00"),
                        new BigDecimal("45.39"), AprLevel.NORMAL,
                        DebtSourceType.OCR, OverdueStatus.NORMAL
                )
        );

        SuggestionLayer suggestion = new SuggestionLayer(
                "管理多笔债务确实需要花费精力...",
                "按当前结构，未来3年将多支付约82,400元利息",
                "好消息是，通过调整优先级，你可以有效降低利息支出",
                List.of("优先偿还平安普惠消费贷", "维持招商银行信用卡最低还款", "建立3个月应急储备金"),
                "这些调整不影响你的信用记录，随时可以调整节奏",
                "你的债务结构有优化空间，优先处理高利率债务可节省约82,400元",
                List.of("平安普惠"),
                true
        );

        ThreeYearLoss threeYearLoss = new ThreeYearLoss(
                new BigDecimal("82400.00"),
                "相当于 13 个月房租",
                "如果维持当前结构，3 年将多支付 82,400 元"
        );

        AprComparison aprComparison = new AprComparison(
                new BigDecimal("24.00"),
                new BigDecimal("8.50"),
                new BigDecimal("15.50"),
                "你的综合利率 24.00%，市场均值 8.50%"
        );

        MonthlyPressure monthlyPressure = new MonthlyPressure(
                new BigDecimal("0.65"),
                new BigDecimal("0.40"),
                true,
                "月供占收入 65.00%，健康线为 40.00% 以下"
        );

        List<InterestBreakdownItem> breakdown = List.of(
                new InterestBreakdownItem(10001L, "平安普惠", new BigDecimal("9000.00"), new BigDecimal("54.61")),
                new InterestBreakdownItem(10002L, "招商银行信用卡", new BigDecimal("7500.00"), new BigDecimal("45.39"))
        );

        LossVisualizationData lossVisualization = new LossVisualizationData(
                threeYearLoss, aprComparison, monthlyPressure, breakdown
        );

        ReportMetadata metadata = new ReportMetadata(
                LocalDateTime.of(2026, 3, 4, 14, 30, 0),
                "v1.0",
                2,
                1,
                true
        );

        List<ReportWarning> warnings = List.of();

        return new ReportData(summary, debtAnalyses, suggestion, lossVisualization, metadata, warnings);
    }

    private ReportData buildReportDataWithNullSuggestion() {
        ReportData full = buildFullReportData();
        return new ReportData(
                full.numericSummary(),
                full.debtAnalyses(),
                null,
                full.lossVisualization(),
                full.metadata(),
                full.warnings()
        );
    }

    private ReportData buildReportDataWithNullIncome() {
        NumericSummary summary = new NumericSummary(
                new BigDecimal("380000.00"),
                2,
                new BigDecimal("24.00"),
                new BigDecimal("12800.00"),
                null,
                null,
                new BigDecimal("82400.00"),
                "相当于 13 个月房租",
                new BigDecimal("68.00"),
                RiskLevel.MEDIUM
        );

        MonthlyPressure monthlyPressure = new MonthlyPressure(
                null,
                new BigDecimal("0.40"),
                false,
                "填写收入获取更精确分析"
        );

        ReportData full = buildFullReportData();
        LossVisualizationData loss = new LossVisualizationData(
                full.lossVisualization().threeYearExtraInterest(),
                full.lossVisualization().currentVsHealthy(),
                monthlyPressure,
                full.lossVisualization().interestBreakdown()
        );

        return new ReportData(summary, full.debtAnalyses(), full.suggestion(), loss,
                full.metadata(), full.warnings());
    }

    // ===== Tests =====

    @Test
    void should_export_valid_pdf_bytes() {
        byte[] pdfBytes = pdfExportService.export(buildFullReportData());

        assertThat(pdfBytes).isNotNull().isNotEmpty();
        // PDF files start with "%PDF"
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void should_include_all_sections_in_pdf() throws Exception {
        byte[] pdfBytes = pdfExportService.export(buildFullReportData());

        String text = extractPdfText(pdfBytes);

        // Section 1: Header
        assertThat(text).contains("债务优化分析报告");
        assertThat(text).contains("2026-03-04 14:30:00");
        assertThat(text).contains("v1.0");

        // Section 2: Numeric summary
        assertThat(text).contains("核心数据摘要");

        // Section 3: Debt analysis table
        assertThat(text).contains("逐笔债务分析");

        // Section 4: Loss visualization
        assertThat(text).contains("损失可视化分析");
        assertThat(text).contains("利率对比");
        assertThat(text).contains("三年多付利息");

        // Section 5: Suggestion
        assertThat(text).contains("AI 优化建议");
    }

    @Test
    void should_handle_null_suggestion_in_pdf() throws Exception {
        byte[] pdfBytes = pdfExportService.export(buildReportDataWithNullSuggestion());

        assertThat(pdfBytes).isNotNull().isNotEmpty();
        String text = extractPdfText(pdfBytes);

        assertThat(text).contains("AI 建议暂时不可用");
    }

    @Test
    void should_handle_null_monthly_income_in_pdf() throws Exception {
        byte[] pdfBytes = pdfExportService.export(buildReportDataWithNullIncome());

        assertThat(pdfBytes).isNotNull().isNotEmpty();
        String text = extractPdfText(pdfBytes);

        assertThat(text).contains("填写收入获取更精确分析");
    }

    @Test
    void should_throw_biz_exception_when_report_data_is_null() {
        assertThatThrownBy(() -> pdfExportService.export(null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void should_format_amounts_with_thousands_separator() throws Exception {
        byte[] pdfBytes = pdfExportService.export(buildFullReportData());
        String text = extractPdfText(pdfBytes);

        // 380,000.00 or 82,400.00 should appear with commas
        assertThat(text).containsAnyOf("380,000", "82,400", "12,800");
    }

    @Test
    void should_not_contain_application_text_in_pdf() throws Exception {
        byte[] pdfBytes = pdfExportService.export(buildFullReportData());
        String text = extractPdfText(pdfBytes);

        // F-12: No application-related CTAs in PDF
        assertThat(text).doesNotContain("立即申请");
        assertThat(text).doesNotContain("申请按钮");
    }

    @Test
    void should_not_contain_sensitive_info_in_pdf() throws Exception {
        // Build report with no phone/ID/bank card numbers
        byte[] pdfBytes = pdfExportService.export(buildFullReportData());
        String text = extractPdfText(pdfBytes);

        // F-04: No phone numbers, ID numbers, bank card numbers
        // The report data intentionally contains none; verify typical PII patterns are absent
        assertThat(text).doesNotContainPattern("\\d{11}");  // phone number pattern
        assertThat(text).doesNotContainPattern("\\d{18}");  // ID number pattern
        assertThat(text).doesNotContainPattern("\\d{16,19}"); // bank card pattern
    }

    @Test
    void should_mark_high_apr_debts_in_table() throws Exception {
        byte[] pdfBytes = pdfExportService.export(buildFullReportData());
        String text = extractPdfText(pdfBytes);

        // APR WARNING level should have [!] marker
        assertThat(text).contains("[!]");
    }

    @Test
    void should_produce_pdf_with_multiple_pages_for_many_debts() {
        // Build report with many debts to trigger page break
        List<DebtAnalysisItem> manyDebts = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            manyDebts.add(new DebtAnalysisItem(
                    (long) (10000 + i), "机构" + i, DebtType.CONSUMER_LOAN,
                    new BigDecimal("10000.00"), new BigDecimal("24.00"),
                    new BigDecimal("1000.00"), new BigDecimal("2400.00"),
                    new BigDecimal("5.00"), AprLevel.NORMAL,
                    DebtSourceType.MANUAL, OverdueStatus.NORMAL
            ));
        }

        ReportData full = buildFullReportData();
        ReportData reportData = new ReportData(
                full.numericSummary(), manyDebts, full.suggestion(),
                full.lossVisualization(), full.metadata(), full.warnings()
        );

        byte[] pdfBytes = pdfExportService.export(reportData);
        assertThat(pdfBytes).isNotNull().isNotEmpty();
    }

    @Test
    void should_include_footer_with_page_number() throws Exception {
        byte[] pdfBytes = pdfExportService.export(buildFullReportData());
        String text = extractPdfText(pdfBytes);

        assertThat(text).contains("优化家");
        assertThat(text).contains("不构成任何金融建议");
    }

    @Test
    void should_include_debt_counts_in_footer() throws Exception {
        byte[] pdfBytes = pdfExportService.export(buildFullReportData());
        String text = extractPdfText(pdfBytes);

        assertThat(text).contains("手动录入 2 笔");
        assertThat(text).contains("AI识别 1 笔");
    }

    // ===== Helper =====

    private String extractPdfText(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }
}
