package com.youhua.engine.apr;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * APR engine configuration bound from application.yml.
 *
 * <pre>
 * youhua:
 *   engine:
 *     apr:
 *       warning-threshold: 36.0
 *       danger-threshold: 100.0
 *       abnormal-threshold: 1000.0
 *       max-allowed: 10000.0
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "youhua.engine.apr")
public class AprConfig {

    /** ≤36% 正常；>36% 标记为高息预警（对应国家司法保护上限） */
    private BigDecimal warningThreshold = new BigDecimal("36.0");

    /** >100% 标记为危险利率 */
    private BigDecimal dangerThreshold = new BigDecimal("100.0");

    /** >1000% 标记为异常（可能是录入错误） */
    private BigDecimal abnormalThreshold = new BigDecimal("1000.0");

    /** >10000% 直接拒绝计算（防止除零或极端输入） */
    private BigDecimal maxAllowed = new BigDecimal("10000.0");
}
