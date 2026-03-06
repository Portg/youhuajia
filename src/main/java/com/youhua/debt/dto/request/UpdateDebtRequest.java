package com.youhua.debt.dto.request;

import com.youhua.debt.dto.response.DebtResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateDebtRequest {

    @NotNull
    @Valid
    private DebtResponse debt;

    /** FieldMask，逗号分隔字段路径，如 "creditor,principal,loanDays"（AIP-134） */
    @NotBlank
    private String updateMask;
}
