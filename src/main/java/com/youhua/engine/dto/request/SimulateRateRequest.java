package com.youhua.engine.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SimulateRateRequest {

    @NotNull
    private BigDecimal currentWeightedApr;

    @NotNull
    private BigDecimal targetApr;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal totalPrincipal;

    @NotNull
    @Min(1)
    private Integer avgLoanDays;

    /** 月收入（可选，用于计算比例变化） */
    private BigDecimal monthlyIncome;
}
