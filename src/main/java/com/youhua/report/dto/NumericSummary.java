package com.youhua.report.dto;

import com.youhua.profile.enums.RiskLevel;

import java.math.BigDecimal;

/**
 * First layer: numeric summary of the user's debt situation.
 * All monetary values use BigDecimal (F-01).
 */
public record NumericSummary(
        BigDecimal totalDebt,
        int debtCount,
        BigDecimal weightedApr,
        BigDecimal monthlyPayment,
        BigDecimal monthlyIncome,
        BigDecimal debtIncomeRatio,
        BigDecimal threeYearExtraInterest,
        String threeYearExtraInterestAnalogy,
        BigDecimal restructureScore,
        RiskLevel riskLevel
) {}
