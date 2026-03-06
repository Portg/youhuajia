package com.youhua.engine.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AssessPressureRequest {

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal monthlyPayment;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal monthlyIncome;
}
