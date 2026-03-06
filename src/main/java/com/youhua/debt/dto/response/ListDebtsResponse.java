package com.youhua.debt.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ListDebtsResponse {

    private List<DebtResponse> debts;
    /** 下一页游标，为空则无更多数据 */
    private String nextPageToken;
    private Integer totalSize;
    private Summary summary;

    @Data
    @Builder
    public static class Summary {
        private Integer totalCount;
        private BigDecimal totalPrincipal;
        private BigDecimal totalMonthlyPayment;
        private Integer confirmedCount;
    }
}
