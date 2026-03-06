package com.youhua.report.dto;

import java.math.BigDecimal;

/**
 * Per-debt interest breakdown for pie chart visualization.
 */
public record InterestBreakdownItem(
        Long debtId,
        String creditor,
        BigDecimal interestAmount,
        BigDecimal percentage
) {}
