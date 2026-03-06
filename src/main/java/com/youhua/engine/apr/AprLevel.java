package com.youhua.engine.apr;

/**
 * APR alert level based on configured thresholds.
 *
 * <ul>
 *   <li>NORMAL: apr <= warningThreshold (36%)</li>
 *   <li>WARNING: apr <= dangerThreshold (100%)</li>
 *   <li>DANGER: apr <= abnormalThreshold (1000%)</li>
 *   <li>ABNORMAL: apr <= maxAllowed (10000%)</li>
 * </ul>
 */
public enum AprLevel {
    NORMAL,
    WARNING,
    DANGER,
    ABNORMAL
}
