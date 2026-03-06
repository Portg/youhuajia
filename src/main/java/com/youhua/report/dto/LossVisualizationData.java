package com.youhua.report.dto;

import java.util.List;

/**
 * Loss visualization data for Page 4 rendering.
 *
 * <p>This DTO is used for frontend charts/visualization — PDF renders text equivalents.
 * F-12: No "申请" (apply) button or related fields are included here.
 */
public record LossVisualizationData(
        ThreeYearLoss threeYearExtraInterest,
        AprComparison currentVsHealthy,
        MonthlyPressure monthlyPressure,
        List<InterestBreakdownItem> interestBreakdown
) {}
