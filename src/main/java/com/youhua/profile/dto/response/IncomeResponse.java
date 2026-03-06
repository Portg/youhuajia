package com.youhua.profile.dto.response;

import com.youhua.profile.enums.IncomeType;
import com.youhua.profile.enums.VerificationStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class IncomeResponse {

    /** 资源名称: incomes/{incomeId} */
    private String name;
    private IncomeType incomeType;
    private BigDecimal amount;
    /** 是否主要收入（AIP-140：布尔字段不加 is 前缀） */
    private Boolean primary;
    private VerificationStatus verificationStatus;
}
