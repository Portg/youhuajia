package com.youhua.report.service;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.engine.apr.AprLevel;
import com.youhua.report.dto.DebtAnalysisItem;
import com.youhua.report.dto.LossVisualizationData;
import com.youhua.report.dto.NumericSummary;
import com.youhua.report.dto.ReportData;
import com.youhua.report.dto.ReportMetadata;
import com.youhua.report.dto.SuggestionLayer;
import lombok.extern.slf4j.Slf4j;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PDF export service for debt optimization reports.
 *
 * <p>Generates A4 portrait PDF with 5 sections:
 * header, numeric summary, debt analysis table, loss visualization, AI suggestions.
 *
 * <p>Security constraints:
 * <ul>
 *   <li>F-04: No phone numbers, ID numbers, or bank card numbers in PDF</li>
 *   <li>F-12: No "申请" (apply) button or application-related text</li>
 *   <li>F-01: All amounts formatted from BigDecimal, never float/double</li>
 * </ul>
 */
@Slf4j
@Service
public class PdfExportService {

    // ===== Layout Constants (A4 portrait) =====
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();    // 595 pt
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();  // 842 pt
    private static final float MARGIN_LEFT = 50f;
    private static final float MARGIN_RIGHT = 50f;
    private static final float MARGIN_TOP = 60f;
    private static final float MARGIN_BOTTOM = 60f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT; // 495 pt

    // ===== Font Sizes =====
    private static final float TITLE_FONT_SIZE = 18f;
    private static final float HEADING_FONT_SIZE = 14f;
    private static final float BODY_FONT_SIZE = 10f;
    private static final float SMALL_FONT_SIZE = 8f;

    // ===== Spacing =====
    private static final float LINE_SPACING = 16f;
    private static final float SECTION_SPACING = 24f;
    private static final float PARAGRAPH_SPACING = 8f;

    // ===== Colors (grayscale approximation for PDFBox) =====
    private static final float[] COLOR_DARK = {0.1f, 0.1f, 0.1f};
    private static final float[] COLOR_GRAY = {0.5f, 0.5f, 0.5f};
    private static final float[] COLOR_LIGHT_GRAY = {0.85f, 0.85f, 0.85f};
    private static final float[] COLOR_WHITE = {1f, 1f, 1f};
    private static final float[] COLOR_VERY_LIGHT_GRAY = {0.95f, 0.95f, 0.95f};

    // ===== Font resource path =====
    private static final String FONT_RESOURCE_PATH = "/fonts/NotoSansSC-Regular.ttf";

    // ===== System CJK font fallback candidates (ordered by preference) =====
    // Standalone TTF files (no collection overhead)
    private static final String[] SYSTEM_CJK_TTF_PATHS = {
            "/Library/Fonts/Arial Unicode.ttf",               // macOS (with CJK)
            "C:\\Windows\\Fonts\\simhei.ttf",                 // Windows
            "C:\\Windows\\Fonts\\simsun.ttf",                 // Windows
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf" // Linux
    };

    // TTC files — need TrueTypeCollection loading (first font in collection used)
    private static final String[] SYSTEM_CJK_TTC_PATHS = {
            "/System/Library/Fonts/STHeiti Medium.ttc",       // macOS
            "/System/Library/Fonts/STHeiti Light.ttc",        // macOS
            "/System/Library/Fonts/Hiragino Sans GB.ttc",     // macOS
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc", // Ubuntu/Debian
            "/usr/share/fonts/wqy-microhei/wqy-microhei.ttc", // CentOS/RHEL
            "C:\\Windows\\Fonts\\msyh.ttc"                    // Windows
    };

    // ===== Formatters =====
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ===== Table column widths (proportional, must sum to CONTENT_WIDTH=495) =====
    private static final float[] TABLE_COL_WIDTHS = {30f, 100f, 65f, 70f, 70f, 65f, 60f, 35f};

    /**
     * Exports a ReportData to PDF bytes.
     *
     * @param reportData assembled report data
     * @return PDF file as byte array
     * @throws BizException REPORT_PDF_FAILED on any rendering error
     */
    public byte[] export(ReportData reportData) {
        if (reportData == null) {
            throw new BizException(ErrorCode.REPORT_PDF_FAILED, "PDF 导出失败：报告数据为空");
        }

        try (PDDocument document = new PDDocument()) {
            PDFont font = loadFont(document);

            // Track pages for footer page numbering
            List<PDPage> pages = new ArrayList<>();

            // Render each section, managing page breaks
            PageState state = new PageState(document, font, pages);
            state.newPage();

            renderHeader(state, reportData.metadata());
            renderNumericSummary(state, reportData.numericSummary());
            renderDebtAnalysisTable(state, reportData.debtAnalyses());
            renderLossVisualization(state, reportData.lossVisualization());
            renderSuggestion(state, reportData.suggestion());

            // Close the last open content stream before rendering footers and saving
            state.close();

            // Render footers on all pages (uses APPEND mode, requires no open streams)
            renderFooters(document, font, pages, reportData.metadata());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            byte[] pdfBytes = out.toByteArray();
            log.debug("PDF exported successfully: {} bytes, {} pages", pdfBytes.length, pages.size());
            return pdfBytes;

        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.error("PDF rendering failed", e);
            throw new BizException(ErrorCode.REPORT_PDF_FAILED, "PDF 导出失败：" + e.getMessage(), e);
        }
    }

    // ===== Font Loading =====

    private PDFont loadFont(PDDocument document) throws IOException {
        // 1. Try classpath font (production path)
        InputStream fontStream = getClass().getResourceAsStream(FONT_RESOURCE_PATH);
        if (fontStream != null) {
            try {
                return PDType0Font.load(document, fontStream, true);
            } catch (IOException e) {
                log.warn("Failed to load classpath font {}: {}", FONT_RESOURCE_PATH, e.getMessage());
            }
        } else {
            log.warn("Font not found at classpath:{} — place NotoSansSC-Regular.ttf in src/main/resources/fonts/",
                    FONT_RESOURCE_PATH);
        }

        // 2a. Try standalone TTF system fonts
        for (String ttfPath : SYSTEM_CJK_TTF_PATHS) {
            File fontFile = new File(ttfPath);
            if (fontFile.exists() && fontFile.canRead()) {
                try (InputStream ttfStream = new FileInputStream(fontFile)) {
                    PDFont font = PDType0Font.load(document, ttfStream, true);
                    log.info("Using system CJK TTF font fallback: {}", ttfPath);
                    return font;
                } catch (IOException e) {
                    log.debug("Could not load TTF system font {}: {}", ttfPath, e.getMessage());
                }
            }
        }

        // 2b. Try TTC (TrueType Collection) system fonts
        for (String ttcPath : SYSTEM_CJK_TTC_PATHS) {
            File fontFile = new File(ttcPath);
            if (fontFile.exists() && fontFile.canRead()) {
                try (TrueTypeCollection collection = new TrueTypeCollection(fontFile)) {
                    TrueTypeFont ttf = collection.getFontByName(null); // first font in collection
                    if (ttf != null) {
                        PDFont font = PDType0Font.load(document, ttf, true);
                        log.info("Using system CJK TTC font fallback: {}", ttcPath);
                        return font;
                    }
                } catch (IOException e) {
                    log.debug("Could not load TTC system font {}: {}", ttcPath, e.getMessage());
                }
            }
        }

        // 3. No CJK font available — throw rather than silently produce garbled PDF
        throw new BizException(ErrorCode.REPORT_PDF_FAILED,
                "PDF 导出失败：未找到中文字体。请将 NotoSansSC-Regular.ttf 放置到 src/main/resources/fonts/");
    }

    // ===== Section 1: Header =====

    private void renderHeader(PageState state, ReportMetadata metadata) throws IOException {
        String title = "优化家 — 债务优化分析报告";
        String timeStr = metadata != null && metadata.generatedTime() != null
                ? "报告生成时间：" + metadata.generatedTime().format(DATETIME_FORMATTER)
                : "报告生成时间：—";
        String versionStr = metadata != null && metadata.scoringModelVersion() != null
                ? "评分模型版本：" + metadata.scoringModelVersion()
                : "评分模型版本：v1.0";

        state.ensureSpace(TITLE_FONT_SIZE + LINE_SPACING * 3);

        // Title — centered, large
        state.drawCenteredText(safeText(title), TITLE_FONT_SIZE, COLOR_DARK);
        state.moveDown(LINE_SPACING);

        // Subtitle lines — centered, gray, small
        state.drawCenteredText(safeText(timeStr), BODY_FONT_SIZE, COLOR_GRAY);
        state.moveDown(LINE_SPACING);
        state.drawCenteredText(safeText(versionStr), BODY_FONT_SIZE, COLOR_GRAY);
        state.moveDown(SECTION_SPACING);

        // Horizontal rule
        state.drawHorizontalLine();
        state.moveDown(SECTION_SPACING);
    }

    // ===== Section 2: Numeric Summary =====

    private void renderNumericSummary(PageState state, NumericSummary summary) throws IOException {
        if (summary == null) return;

        state.ensureSpace(HEADING_FONT_SIZE + LINE_SPACING * 6);

        state.drawText("核心数据摘要", HEADING_FONT_SIZE, COLOR_DARK);
        state.moveDown(LINE_SPACING + PARAGRAPH_SPACING);

        // Four data cards in a row
        String totalDebtLabel = "总负债";
        String totalDebtValue = formatAmount(summary.totalDebt()) + " 元";
        String aprLabel = "加权APR";
        String aprValue = formatPercent(summary.weightedApr()) + "%";
        String paymentLabel = "月供总额";
        String paymentValue = formatAmount(summary.monthlyPayment()) + " 元";
        String scoreLabel = "重组评分";
        String scoreValue = formatScore(summary.restructureScore()) + " 分";

        renderFourCardRow(state, totalDebtLabel, totalDebtValue, aprLabel, aprValue,
                paymentLabel, paymentValue, scoreLabel, scoreValue);
        state.moveDown(LINE_SPACING);

        // Three-year extra interest highlight
        if (summary.threeYearExtraInterest() != null) {
            String interestText = "三年多付利息：" + formatAmount(summary.threeYearExtraInterest()) + " 元";
            if (summary.threeYearExtraInterestAnalogy() != null) {
                interestText += "（" + safeText(summary.threeYearExtraInterestAnalogy()) + "）";
            }
            state.drawText(safeText(interestText), HEADING_FONT_SIZE, COLOR_DARK);
            state.moveDown(LINE_SPACING);
        }

        // Risk level
        String riskText = "风险等级：" + (summary.riskLevel() != null ? summary.riskLevel().getDescription() : "—");
        state.drawText(safeText(riskText), BODY_FONT_SIZE, COLOR_DARK);
        state.moveDown(SECTION_SPACING);
    }

    private void renderFourCardRow(PageState state,
                                    String label1, String value1,
                                    String label2, String value2,
                                    String label3, String value3,
                                    String label4, String value4) throws IOException {
        float cardWidth = CONTENT_WIDTH / 4f;
        float cardHeight = LINE_SPACING * 2.5f;
        float startX = MARGIN_LEFT;
        float y = state.currentY;

        String[] labels = {label1, label2, label3, label4};
        String[] values = {value1, value2, value3, value4};

        PDPageContentStream cs = state.currentStream;

        for (int i = 0; i < 4; i++) {
            float x = startX + i * cardWidth;
            // Card background
            cs.setNonStrokingColor(COLOR_VERY_LIGHT_GRAY[0], COLOR_VERY_LIGHT_GRAY[1], COLOR_VERY_LIGHT_GRAY[2]);
            cs.addRect(x + 2, y - cardHeight, cardWidth - 4, cardHeight);
            cs.fill();

            // Card label
            cs.setNonStrokingColor(COLOR_GRAY[0], COLOR_GRAY[1], COLOR_GRAY[2]);
            cs.beginText();
            cs.setFont(state.font, SMALL_FONT_SIZE);
            cs.newLineAtOffset(x + 6, y - SMALL_FONT_SIZE - 4);
            cs.showText(safeText(labels[i]));
            cs.endText();

            // Card value
            cs.setNonStrokingColor(COLOR_DARK[0], COLOR_DARK[1], COLOR_DARK[2]);
            cs.beginText();
            cs.setFont(state.font, BODY_FONT_SIZE);
            cs.newLineAtOffset(x + 6, y - cardHeight + 6);
            cs.showText(safeText(values[i]));
            cs.endText();
        }
        state.moveDown(cardHeight + PARAGRAPH_SPACING);
    }

    // ===== Section 3: Debt Analysis Table =====

    private void renderDebtAnalysisTable(PageState state, List<DebtAnalysisItem> items) throws IOException {
        if (items == null || items.isEmpty()) return;

        state.ensureSpace(HEADING_FONT_SIZE + LINE_SPACING * 3);

        state.drawText("逐笔债务分析", HEADING_FONT_SIZE, COLOR_DARK);
        state.moveDown(LINE_SPACING + PARAGRAPH_SPACING);

        // Table header
        String[] headers = {"序号", "债权机构", "类型", "本金(元)", "年化利率(%)", "月供(元)", "利息占比(%)", "状态"};
        renderTableRow(state, headers, true, false);

        // Data rows
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalMonthlyPayment = BigDecimal.ZERO;

        for (int i = 0; i < items.size(); i++) {
            DebtAnalysisItem item = items.get(i);
            boolean alternateRow = (i % 2 == 1);

            String aprStr = formatPercent(item.apr());
            if (item.aprLevel() != null && item.aprLevel() != AprLevel.NORMAL) {
                aprStr += " [!]";
            }

            String overdueStr = formatOverdueStatus(item.overdueStatus());

            String[] row = {
                    String.valueOf(i + 1),
                    safeText(item.creditor()),
                    item.debtType() != null ? item.debtType().getDescription() : "—",
                    formatAmount(item.principal()),
                    aprStr,
                    formatAmount(item.monthlyPayment()),
                    formatPercent(item.interestContribution()),
                    overdueStr
            };

            renderTableRow(state, row, false, alternateRow);

            if (item.principal() != null) {
                totalPrincipal = totalPrincipal.add(item.principal());
            }
            if (item.monthlyPayment() != null) {
                totalMonthlyPayment = totalMonthlyPayment.add(item.monthlyPayment());
            }
        }

        // Total row
        String[] totalRow = {
                "合计", "", "", formatAmount(totalPrincipal),
                "—", formatAmount(totalMonthlyPayment), "100.00", "—"
        };
        renderTableRow(state, totalRow, true, false);
        state.moveDown(SECTION_SPACING);
    }

    private void renderTableRow(PageState state, String[] cells, boolean bold, boolean alternate)
            throws IOException {
        float rowHeight = LINE_SPACING + 6f;
        state.ensureSpace(rowHeight + 4);

        float startX = MARGIN_LEFT;
        float y = state.currentY;

        PDPageContentStream cs = state.currentStream;

        // Row background
        float[] bgColor = bold ? COLOR_LIGHT_GRAY : (alternate ? COLOR_VERY_LIGHT_GRAY : COLOR_WHITE);
        cs.setNonStrokingColor(bgColor[0], bgColor[1], bgColor[2]);
        cs.addRect(startX, y - rowHeight, CONTENT_WIDTH, rowHeight);
        cs.fill();

        // Cell text
        float xPos = startX;
        float fontSize = bold ? BODY_FONT_SIZE : SMALL_FONT_SIZE;
        cs.setNonStrokingColor(COLOR_DARK[0], COLOR_DARK[1], COLOR_DARK[2]);

        for (int i = 0; i < cells.length && i < TABLE_COL_WIDTHS.length; i++) {
            String text = cells[i] != null ? safeText(cells[i]) : "";
            cs.beginText();
            cs.setFont(state.font, fontSize);
            cs.newLineAtOffset(xPos + 3, y - rowHeight + 5);
            // Truncate text to fit in column
            text = truncateText(text, TABLE_COL_WIDTHS[i] - 6, state.font, fontSize);
            cs.showText(text);
            cs.endText();
            xPos += TABLE_COL_WIDTHS[i];
        }

        // Bottom border
        cs.setStrokingColor(COLOR_LIGHT_GRAY[0], COLOR_LIGHT_GRAY[1], COLOR_LIGHT_GRAY[2]);
        cs.moveTo(startX, y - rowHeight);
        cs.lineTo(startX + CONTENT_WIDTH, y - rowHeight);
        cs.stroke();

        state.moveDown(rowHeight);
    }

    // ===== Section 4: Loss Visualization =====

    private void renderLossVisualization(PageState state, LossVisualizationData loss) throws IOException {
        if (loss == null) return;

        state.ensureSpace(HEADING_FONT_SIZE + LINE_SPACING * 8);

        state.drawText("损失可视化分析", HEADING_FONT_SIZE, COLOR_DARK);
        state.moveDown(LINE_SPACING + PARAGRAPH_SPACING);

        // APR Comparison
        if (loss.currentVsHealthy() != null) {
            state.drawText("■ 利率对比", BODY_FONT_SIZE, COLOR_DARK);
            state.moveDown(LINE_SPACING);
            state.drawIndentedText("你的综合利率：" + formatPercent(loss.currentVsHealthy().currentWeightedApr()) + "%",
                    BODY_FONT_SIZE, COLOR_DARK, 16f);
            state.moveDown(LINE_SPACING);
            state.drawIndentedText("市场平均利率：" + formatPercent(loss.currentVsHealthy().marketAvgApr()) + "%",
                    BODY_FONT_SIZE, COLOR_DARK, 16f);
            state.moveDown(LINE_SPACING);
            if (loss.currentVsHealthy().gap() != null) {
                String gapText;
                if (loss.currentVsHealthy().gap().compareTo(BigDecimal.ZERO) > 0) {
                    gapText = "差距：" + formatPercent(loss.currentVsHealthy().gap()) + " 个百分点";
                } else {
                    gapText = "你的综合利率低于市场均值，表现良好";
                }
                state.drawIndentedText(safeText(gapText), BODY_FONT_SIZE, COLOR_DARK, 16f);
                state.moveDown(LINE_SPACING);
            }
            state.moveDown(PARAGRAPH_SPACING);
        }

        // Monthly Pressure
        if (loss.monthlyPressure() != null) {
            state.drawText("■ 月供压力", BODY_FONT_SIZE, COLOR_DARK);
            state.moveDown(LINE_SPACING);
            if (loss.monthlyPressure().displayed()) {
                state.drawIndentedText("月供占收入比：" + formatPercent(loss.monthlyPressure().ratio()) + "%",
                        BODY_FONT_SIZE, COLOR_DARK, 16f);
                state.moveDown(LINE_SPACING);
                state.drawIndentedText("健康线：" + formatPercent(loss.monthlyPressure().healthyLine()) + "% 以下",
                        BODY_FONT_SIZE, COLOR_DARK, 16f);
                state.moveDown(LINE_SPACING);
            } else {
                state.drawIndentedText("填写收入获取更精确分析", BODY_FONT_SIZE, COLOR_GRAY, 16f);
                state.moveDown(LINE_SPACING);
            }
            state.moveDown(PARAGRAPH_SPACING);
        }

        // Three-year extra interest
        if (loss.threeYearExtraInterest() != null) {
            state.drawText("■ 三年多付利息", BODY_FONT_SIZE, COLOR_DARK);
            state.moveDown(LINE_SPACING);
            state.drawIndentedText(formatAmount(loss.threeYearExtraInterest().value()) + " 元",
                    HEADING_FONT_SIZE, COLOR_DARK, 16f);
            state.moveDown(LINE_SPACING);
            if (loss.threeYearExtraInterest().analogy() != null) {
                state.drawIndentedText(safeText(loss.threeYearExtraInterest().analogy()),
                        BODY_FONT_SIZE, COLOR_GRAY, 16f);
                state.moveDown(LINE_SPACING);
            }
            state.moveDown(PARAGRAPH_SPACING);
        }

        state.moveDown(SECTION_SPACING - PARAGRAPH_SPACING);
    }

    // ===== Section 5: AI Suggestions =====

    private void renderSuggestion(PageState state, SuggestionLayer suggestion) throws IOException {
        state.ensureSpace(HEADING_FONT_SIZE + LINE_SPACING * 4);

        state.drawText("AI 优化建议", HEADING_FONT_SIZE, COLOR_DARK);
        state.moveDown(LINE_SPACING + PARAGRAPH_SPACING);

        if (suggestion == null) {
            state.drawText("■ 分析总结", BODY_FONT_SIZE, COLOR_DARK);
            state.moveDown(LINE_SPACING);
            state.drawIndentedText("AI 建议暂时不可用，请在线查看报告获取完整建议。",
                    BODY_FONT_SIZE, COLOR_GRAY, 16f);
            state.moveDown(LINE_SPACING);
            return;
        }

        // Summary
        if (suggestion.summary() != null) {
            state.drawText("■ 分析总结", BODY_FONT_SIZE, COLOR_DARK);
            state.moveDown(LINE_SPACING);
            renderWrappedText(state, safeText(suggestion.summary()), BODY_FONT_SIZE, COLOR_DARK, 16f);
            state.moveDown(PARAGRAPH_SPACING);
        }

        // Action steps
        if (suggestion.actionSteps() != null && !suggestion.actionSteps().isEmpty()) {
            state.drawText("■ 建议行动步骤", BODY_FONT_SIZE, COLOR_DARK);
            state.moveDown(LINE_SPACING);
            List<String> steps = suggestion.actionSteps();
            for (int i = 0; i < steps.size(); i++) {
                state.ensureSpace(LINE_SPACING + 4);
                state.drawIndentedText((i + 1) + ". " + safeText(steps.get(i)),
                        BODY_FONT_SIZE, COLOR_DARK, 16f);
                state.moveDown(LINE_SPACING);
            }
            state.moveDown(PARAGRAPH_SPACING);
        }

        // Priority creditors
        if (suggestion.priorityCreditors() != null && !suggestion.priorityCreditors().isEmpty()) {
            state.drawText("■ 优先处理的债务", BODY_FONT_SIZE, COLOR_DARK);
            state.moveDown(LINE_SPACING);
            state.drawIndentedText(String.join("、", suggestion.priorityCreditors()),
                    BODY_FONT_SIZE, COLOR_DARK, 16f);
            state.moveDown(LINE_SPACING);
        }
    }

    // ===== Footer =====

    private void renderFooters(PDDocument document, PDFont font, List<PDPage> pages, ReportMetadata metadata)
            throws IOException {
        int totalPages = pages.size();
        int manualCount = metadata != null ? metadata.manualCount() : 0;
        int ocrCount = metadata != null ? metadata.ocrCount() : 0;

        for (int i = 0; i < pages.size(); i++) {
            PDPage page = pages.get(i);
            try (PDPageContentStream cs = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true)) {

                float footerY = MARGIN_BOTTOM - 10f;

                // Horizontal rule above footer
                cs.setStrokingColor(COLOR_LIGHT_GRAY[0], COLOR_LIGHT_GRAY[1], COLOR_LIGHT_GRAY[2]);
                cs.moveTo(MARGIN_LEFT, footerY + 22f);
                cs.lineTo(PAGE_WIDTH - MARGIN_RIGHT, footerY + 22f);
                cs.stroke();

                // Disclaimer line 1
                cs.setNonStrokingColor(COLOR_GRAY[0], COLOR_GRAY[1], COLOR_GRAY[2]);
                cs.beginText();
                cs.setFont(font, SMALL_FONT_SIZE);
                cs.newLineAtOffset(MARGIN_LEFT, footerY + 12f);
                cs.showText("本报告基于您提供的债务和收入数据生成，仅供参考，不构成任何金融建议。");
                cs.endText();

                // Data source line
                String sourceText = "数据来源：手动录入 " + manualCount + " 笔、AI识别 " + ocrCount + " 笔";
                cs.beginText();
                cs.setFont(font, SMALL_FONT_SIZE);
                cs.newLineAtOffset(MARGIN_LEFT, footerY + 0f);
                cs.showText(sourceText);
                cs.endText();

                // Page number (right-aligned)
                String pageStr = "优化家 · 第 " + (i + 1) + "/" + totalPages + " 页";
                float pageStrWidth = estimateTextWidth(pageStr, font, SMALL_FONT_SIZE);
                cs.beginText();
                cs.setFont(font, SMALL_FONT_SIZE);
                cs.newLineAtOffset(PAGE_WIDTH - MARGIN_RIGHT - pageStrWidth, footerY + 0f);
                cs.showText(pageStr);
                cs.endText();
            }
        }
    }

    // ===== Formatting Helpers =====

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "—";
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount);
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) return "—";
        return new DecimalFormat("0.00").format(value);
    }

    private String formatScore(BigDecimal score) {
        if (score == null) return "—";
        return new DecimalFormat("0.00").format(score);
    }

    private String formatOverdueStatus(OverdueStatus status) {
        if (status == null) return "正常";
        return status.getDescription();
    }

    /**
     * Sanitizes text to be safe for PDF rendering.
     * Removes null chars and trims excessive whitespace.
     * F-04: caller must not pass phone numbers, ID numbers, or bank card numbers.
     */
    private String safeText(String text) {
        if (text == null) return "—";
        return text.replace("\u0000", "").trim();
    }

    private void renderWrappedText(PageState state, String text, float fontSize,
                                    float[] color, float indent) throws IOException {
        if (text == null || text.isEmpty()) return;
        // Simple word wrapping based on estimated char width
        float maxWidth = CONTENT_WIDTH - indent;
        float charWidth = estimateCharWidth(state.font, fontSize);
        int charsPerLine = Math.max(1, (int) (maxWidth / charWidth));

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + charsPerLine, text.length());
            String line = text.substring(start, end);
            state.ensureSpace(fontSize + 4);
            state.drawIndentedText(line, fontSize, color, indent);
            state.moveDown(fontSize + 4);
            start = end;
        }
    }

    private String truncateText(String text, float maxWidth, PDFont font, float fontSize) {
        if (text == null || text.isEmpty()) return "";
        float charWidth = estimateCharWidth(font, fontSize);
        int maxChars = Math.max(1, (int) (maxWidth / charWidth));
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 2)) + "..";
    }

    private float estimateCharWidth(PDFont font, float fontSize) {
        // Approximate: CJK chars ~full width, ASCII ~half width. Use 0.6 as average.
        return fontSize * 0.6f;
    }

    private float estimateTextWidth(String text, PDFont font, float fontSize) {
        return estimateCharWidth(font, fontSize) * text.length();
    }

    // ===== Inner PageState class =====

    /**
     * Tracks current rendering position and handles page breaks.
     */
    private class PageState {
        final PDDocument document;
        final PDFont font;
        final List<PDPage> pages;
        PDPageContentStream currentStream;
        float currentY;

        PageState(PDDocument document, PDFont font, List<PDPage> pages) {
            this.document = document;
            this.font = font;
            this.pages = pages;
        }

        void newPage() throws IOException {
            if (currentStream != null) {
                currentStream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            pages.add(page);
            currentStream = new PDPageContentStream(document, page);
            currentY = PAGE_HEIGHT - MARGIN_TOP;
        }

        void close() throws IOException {
            if (currentStream != null) {
                currentStream.close();
                currentStream = null;
            }
        }

        void ensureSpace(float requiredHeight) throws IOException {
            if (currentY - requiredHeight < MARGIN_BOTTOM + 30f) {
                newPage();
            }
        }

        void moveDown(float amount) {
            currentY -= amount;
        }

        void drawText(String text, float fontSize, float[] color) throws IOException {
            currentStream.setNonStrokingColor(color[0], color[1], color[2]);
            currentStream.beginText();
            currentStream.setFont(font, fontSize);
            currentStream.newLineAtOffset(MARGIN_LEFT, currentY);
            currentStream.showText(safeText(text));
            currentStream.endText();
        }

        void drawIndentedText(String text, float fontSize, float[] color, float indent) throws IOException {
            currentStream.setNonStrokingColor(color[0], color[1], color[2]);
            currentStream.beginText();
            currentStream.setFont(font, fontSize);
            currentStream.newLineAtOffset(MARGIN_LEFT + indent, currentY);
            currentStream.showText(safeText(text));
            currentStream.endText();
        }

        void drawCenteredText(String text, float fontSize, float[] color) throws IOException {
            float textWidth = estimateTextWidth(text, font, fontSize);
            float x = (PAGE_WIDTH - textWidth) / 2f;
            currentStream.setNonStrokingColor(color[0], color[1], color[2]);
            currentStream.beginText();
            currentStream.setFont(font, fontSize);
            currentStream.newLineAtOffset(x, currentY);
            currentStream.showText(safeText(text));
            currentStream.endText();
        }

        void drawHorizontalLine() throws IOException {
            currentStream.setStrokingColor(COLOR_LIGHT_GRAY[0], COLOR_LIGHT_GRAY[1], COLOR_LIGHT_GRAY[2]);
            currentStream.moveTo(MARGIN_LEFT, currentY);
            currentStream.lineTo(PAGE_WIDTH - MARGIN_RIGHT, currentY);
            currentStream.stroke();
        }
    }
}
