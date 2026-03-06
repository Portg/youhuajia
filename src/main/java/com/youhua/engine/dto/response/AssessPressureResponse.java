package com.youhua.engine.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AssessPressureResponse {

    /** 压力指数 0-100 */
    private BigDecimal pressureIndex;
    /** 压力等级：HEALTHY/MODERATE/HEAVY/SEVERE */
    private PressureLevel level;
    /** 月供收入比 */
    private BigDecimal ratio;
    /** 引导语，如：录入具体债务，获得精确分析 */
    private String hint;

    public enum PressureLevel {
        HEALTHY, MODERATE, HEAVY, SEVERE
    }
}
