package com.youhua.debt.dto.response;

import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.enums.DebtType;
import com.youhua.debt.enums.OverdueStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class DebtResponse {

    /** 资源名称，格式: debts/{debtId} */
    private String name;
    private String creditor;
    private DebtType debtType;
    private BigDecimal principal;
    private BigDecimal totalRepayment;
    private BigDecimal nominalRate;
    /** 实际年化利率（百分比，系统计算） */
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
    /** OCR 置信度（0-100） */
    private BigDecimal confidenceScore;
    private DebtStatus status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer version;

    public static DebtResponse fromEntity(Debt debt) {
        return DebtResponse.builder()
                .name("debts/" + debt.getId())
                .creditor(debt.getCreditor())
                .debtType(debt.getDebtType())
                .principal(debt.getPrincipal())
                .totalRepayment(debt.getTotalRepayment())
                .nominalRate(debt.getNominalRate())
                .apr(debt.getApr())
                .loanDays(debt.getLoanDays())
                .monthlyPayment(debt.getMonthlyPayment())
                .remainingPrincipal(debt.getRemainingPrincipal())
                .remainingPeriods(debt.getRemainingPeriods())
                .startDate(debt.getStartDate())
                .endDate(debt.getEndDate())
                .overdueStatus(debt.getOverdueStatus())
                .overdueDays(debt.getOverdueDays())
                .sourceType(debt.getSourceType())
                .confidenceScore(debt.getConfidenceScore())
                .status(debt.getStatus())
                .remark(debt.getRemark())
                .createTime(debt.getCreateTime())
                .updateTime(debt.getUpdateTime())
                .version(debt.getVersion())
                .build();
    }
}
