package com.youhua.profile.dto.request;

import com.youhua.profile.enums.IncomeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BatchCreateIncomesRequest {

    @NotEmpty
    @Valid
    private List<IncomeInput> incomes;

    @Data
    public static class IncomeInput {

        @NotNull
        private IncomeType incomeType;

        @NotNull
        @DecimalMin(value = "0")
        private BigDecimal amount;

        /** 是否主要收入（AIP-140：布尔字段不加 is 前缀） */
        private Boolean primary;
    }
}
