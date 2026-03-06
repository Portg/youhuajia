package com.youhua.report.dto;

import java.math.BigDecimal;

/**
 * Monthly payment pressure relative to income and healthy threshold.
 * Not displayed when income data is absent.
 */
public record MonthlyPressure(
        BigDecimal ratio,
        BigDecimal healthyLine,
        boolean displayed,
        String displayFormat
) {}
