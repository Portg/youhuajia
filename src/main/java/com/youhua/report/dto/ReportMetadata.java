package com.youhua.report.dto;

import java.time.LocalDateTime;

/**
 * Report generation metadata for footer and audit trail.
 */
public record ReportMetadata(
        LocalDateTime generatedTime,
        String scoringModelVersion,
        int manualCount,
        int ocrCount,
        boolean incomeProvided
) {}
