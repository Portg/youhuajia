package com.youhua.plan.controller;

import com.youhua.plan.dto.request.UpsertImprovementPlanRequest;
import com.youhua.plan.dto.response.ImprovementPlanResponse;
import com.youhua.plan.service.ImprovementPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "improvement-plans", description = "改善计划")
@RestController
@RequestMapping("/api/v1/improvement-plans")
@RequiredArgsConstructor
public class ImprovementPlanController {

    private final ImprovementPlanService planService;

    @Operation(summary = "查询当前用户改善计划（无记录返回全 false 默认值）")
    @GetMapping("/mine")
    public ImprovementPlanResponse getMyPlan() {
        return planService.getMyPlan();
    }

    @Operation(summary = "保存/更新改善计划状态（upsert，不保留历史）")
    @PatchMapping("/mine")
    public ImprovementPlanResponse upsertMyPlan(@RequestBody UpsertImprovementPlanRequest request) {
        return planService.upsertMyPlan(request);
    }

    @Operation(summary = "删除改善计划（重新评估时清空）")
    @DeleteMapping("/mine")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyPlan() {
        planService.deleteMyPlan();
    }
}
