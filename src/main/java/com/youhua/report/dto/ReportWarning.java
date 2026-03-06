package com.youhua.report.dto;

/**
 * Warning indicating data uncertainty in the report.
 */
public record ReportWarning(
        WarningType type,
        String message
) {}
