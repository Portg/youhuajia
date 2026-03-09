package com.youhua.plan.dto.request;

import lombok.Data;

/**
 * 改善计划 upsert 请求。
 * 所有字段可选；null 表示"不更新"，false 表示"明确设置为未完成"。
 */
@Data
public class UpsertImprovementPlanRequest {

    private Boolean layer1Completed;

    private Long layer1ReportId;

    private Boolean layer2Completed;

    private Boolean layer3Completed;
}
