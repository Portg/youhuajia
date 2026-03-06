package com.youhua.report.dto;

import java.math.BigDecimal;

/**
 * Current weighted APR vs. market average comparison data.
 */
public record AprComparison(
        BigDecimal currentWeightedApr,
        BigDecimal marketAvgApr,
        BigDecimal gap,
        String displayFormat
) {}
