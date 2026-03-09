package com.youhua.plan.service;

import com.youhua.plan.dto.request.UpsertImprovementPlanRequest;
import com.youhua.plan.dto.response.ImprovementPlanResponse;

public interface ImprovementPlanService {

    /**
     * 保存或更新当前用户的改善计划状态（upsert，不保留历史）。
     * null 字段保留现有值，非 null 字段覆盖写入。
     */
    ImprovementPlanResponse upsertMyPlan(UpsertImprovementPlanRequest request);

    /**
     * 查询当前用户的改善计划。无记录时返回 empty()。
     */
    ImprovementPlanResponse getMyPlan();

    /**
     * 删除当前用户的改善计划（逻辑删除，「重新评估」时调用）。
     */
    void deleteMyPlan();
}
