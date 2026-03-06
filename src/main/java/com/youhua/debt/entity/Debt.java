package com.youhua.debt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.youhua.common.entity.BaseVersionEntity;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.enums.DebtType;
import com.youhua.debt.enums.OverdueStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@TableName("t_debt")
public class Debt extends BaseVersionEntity {

    private Long userId;

    private String creditor;

    private DebtType debtType;

    private BigDecimal principal;

    private BigDecimal totalRepayment;

    private BigDecimal nominalRate;

    private BigDecimal apr;

    private Integer loanDays;

    private BigDecimal monthlyPayment;

    private BigDecimal remainingPrincipal;

    private Integer remainingPeriods;

    private LocalDate startDate;

    private LocalDate endDate;

    private OverdueStatus overdueStatus;

    private Integer overdueDays;

    private DebtSourceType sourceType;

    private Long ocrTaskId;

    private BigDecimal confidenceScore;

    private DebtStatus status;

    private String remark;

    // ---- V2.0 预留 ----
    private String bankSyncId;

    // ---- V2.1 预留 ----
    private Long familyId;
}
