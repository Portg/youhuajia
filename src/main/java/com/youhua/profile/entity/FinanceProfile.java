package com.youhua.profile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.youhua.common.entity.BaseVersionEntity;
import com.youhua.profile.enums.ProfileGenerationStatus;
import com.youhua.profile.enums.RiskLevel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_finance_profile")
public class FinanceProfile extends BaseVersionEntity {

    private Long userId;

    private BigDecimal totalDebt;

    private Integer debtCount;

    private BigDecimal weightedApr;

    private BigDecimal monthlyPayment;

    private BigDecimal monthlyIncome;

    private BigDecimal debtIncomeRatio;

    private BigDecimal liquidityScore;

    private BigDecimal restructureScore;

    private RiskLevel riskLevel;

    private Integer overdueCount;

    private Long highestAprDebtId;

    private String scoreDetailJson;

    private LocalDateTime lastCalculatedTime;

    private ProfileGenerationStatus generationStatus;

    private Integer generationRetryCount;

    // ---- V2.0 预留 ----
    private BigDecimal assetTotal;

    private BigDecimal netWorth;

    // ---- V2.1 预留 ----
    private Long familyProfileId;
}
