package com.youhua.engine.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CalculateAprRequest {

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal principal;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal totalRepayment;

    @NotNull
    @Min(1)
    private Integer loanDays;
}
