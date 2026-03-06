package com.youhua.debt.dto.request;

import com.youhua.debt.enums.DebtType;
import com.youhua.debt.enums.OverdueStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateDebtRequest {

    /** 幂等键（AIP-155），UUID4 格式，防重复提交 */
    private String requestId;

    @NotNull
    @Valid
    private DebtInput debt;

    @Data
    public static class DebtInput {

        @NotBlank
        @Size(min = 1, max = 100)
        private String creditor;

        @NotNull
        private DebtType debtType;

        @NotNull
        @DecimalMin(value = "0.01")
        private BigDecimal principal;

        @NotNull
        @DecimalMin(value = "0.01")
        private BigDecimal totalRepayment;

        private BigDecimal nominalRate;

        @NotNull
        @Min(1)
        private Integer loanDays;

        private BigDecimal monthlyPayment;
        private BigDecimal remainingPrincipal;
        private Integer remainingPeriods;
        private LocalDate startDate;
        private LocalDate endDate;
        private OverdueStatus overdueStatus;

        @Min(0)
        private Integer overdueDays;

        @Size(max = 500)
        private String remark;
    }
}
