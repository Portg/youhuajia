package com.youhua.profile.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ReportResponse {

    /** 资源名称: reports/{reportId} */
    private String name;
    private FinanceProfileResponse profileSnapshot;
    private List<PriorityItem> priorityList;
    /** 90天行动计划 */
    private Map<String, Object> actionPlan;
    /** AI 生成的文字建议 */
    private String aiSummary;
    private List<String> riskWarnings;
    private Integer reportVersion;
    private LocalDateTime createTime;

    @Data
    @Builder
    public static class PriorityItem {
        private Integer rank;
        /** 债务资源名称: debts/{debtId} */
        private String debt;
        private String creditor;
        private BigDecimal apr;
        private String reason;
    }
}
