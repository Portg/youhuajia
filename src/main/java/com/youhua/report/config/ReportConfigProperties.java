package com.youhua.report.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Externalized configuration for report module thresholds and reference values.
 * All business thresholds must be configured here, not hardcoded (F-09).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "youhua.report")
public class ReportConfigProperties {

    private BigDecimal marketAvgApr = new BigDecimal("8.5");

    private BigDecimal avgMonthlyRent = new BigDecimal("6000.00");

    private BigDecimal healthyDebtIncomeRatio = new BigDecimal("0.40");

    private String scoringModelVersion = "v1.0";
}
