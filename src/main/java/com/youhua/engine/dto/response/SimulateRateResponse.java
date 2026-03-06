package com.youhua.engine.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SimulateRateResponse {

    private BigDecimal currentMonthlyPayment;
    private BigDecimal targetMonthlyPayment;
    private BigDecimal monthlySaving;
    private BigDecimal threeYearSaving;
    /** 当前月供收入比 */
    private BigDecimal currentIncomeRatio;
    /** 目标月供收入比 */
    private BigDecimal targetIncomeRatio;
    private String disclaimer;
}
