package com.youhua.engine.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CalculateAprResponse {

    /** 年化利率（百分比） */
    private BigDecimal apr;
    private BigDecimal dailyRate;
    private BigDecimal totalInterest;
}
