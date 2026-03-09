package com.youhua.profile.dto.response;

import com.youhua.profile.enums.RiskLevel;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class FinanceProfileResponse {

    /** 资源名称: users/{userId}/finance-profile */
    private String name;
    private BigDecimal totalDebt;
    private Integer debtCount;
    /** 加权年化利率（百分比） */
    private BigDecimal weightedApr;
    private BigDecimal monthlyPayment;
    private BigDecimal monthlyIncome;
    private BigDecimal debtIncomeRatio;
    private BigDecimal liquidityScore;
    /** 重组评分（0-100） */
    private BigDecimal restructureScore;
    private RiskLevel riskLevel;
    /** 评分各维度明细（DimensionDetail 列表） */
    private List<Map<String, Object>> scoreDimensions;
    private LocalDateTime lastCalculateTime;
    /** 3年多付利息（相对市场基准利率，用于 Page4 核心展示） */
    private BigDecimal threeYearExtraInterest;
    /** 平均贷款天数（用于 Page6 利率模拟器） */
    private Integer avgLoanDays;
    /** 最高利率债权人名称（用于 Page9 展示） */
    private String highestAprCreditor;
    /** APR > 24% 的高息债务笔数（用于 Page4 展示） */
    private Integer highInterestDebtCount;
    /** 逾期笔数（前端 scoreSimulator CATCH_UP_PAYMENTS 模拟用） */
    private Integer overdueCount;
    /** 最大逾期天数（前端 scoreSimulator 分档评分用） */
    private Integer maxOverdueDays;
    /** 房贷笔数（前端分群匹配 MORTGAGE_HEAVY 用） */
    private Integer mortgageCount;
}
