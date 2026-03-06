package com.youhua.report.dto;

import java.util.List;

/**
 * Complete report data containing three-layer explainability structure.
 *
 * <p>Layer 1: numericSummary — key numbers
 * <p>Layer 2: debtAnalyses — per-debt breakdown
 * <p>Layer 3: suggestion — AI optimization advice (may be null in degraded mode)
 *
 * <p>F-12: No application-related fields or CTAs are present in this DTO.
 */
public record ReportData(
        NumericSummary numericSummary,
        List<DebtAnalysisItem> debtAnalyses,
        SuggestionLayer suggestion,
        LossVisualizationData lossVisualization,
        ReportMetadata metadata,
        List<ReportWarning> warnings
) {}
