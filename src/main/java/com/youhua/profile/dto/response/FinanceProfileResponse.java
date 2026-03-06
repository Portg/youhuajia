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
}
