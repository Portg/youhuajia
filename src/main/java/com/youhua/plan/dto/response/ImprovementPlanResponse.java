package com.youhua.plan.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ImprovementPlanResponse {

    private Boolean layer1Completed;

    private Long layer1ReportId;

    private Boolean layer2Completed;

    private Boolean layer3Completed;

    private LocalDateTime updateTime;

    /** 返回空默认值（所有层未完成）用于用户无历史记录场景 */
    public static ImprovementPlanResponse empty() {
        return ImprovementPlanResponse.builder()
                .layer1Completed(false)
                .layer2Completed(false)
                .layer3Completed(false)
                .build();
    }
}
