package com.youhua.report.dto;

import java.math.BigDecimal;

/**
 * Three-year extra interest loss projection.
 * Value is calculated by the Java engine, not by AI (F-02).
 */
public record ThreeYearLoss(
        BigDecimal value,
        String analogy,
        String displayFormat
) {}
