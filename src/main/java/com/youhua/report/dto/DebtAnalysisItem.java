package com.youhua.report.dto;

import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtType;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.engine.apr.AprLevel;

import java.math.BigDecimal;

/**
 * Second layer: per-debt analysis item, sorted by APR descending.
 * All monetary values use BigDecimal (F-01).
 */
public record DebtAnalysisItem(
        Long debtId,
        String creditor,
        DebtType debtType,
        BigDecimal principal,
        BigDecimal apr,
        BigDecimal monthlyPayment,
        BigDecimal totalInterest,
        BigDecimal interestContribution,
        AprLevel aprLevel,
        DebtSourceType sourceType,
        OverdueStatus overdueStatus
) {}
