package com.youhua.plan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youhua.common.util.RequestContextUtil;
import com.youhua.plan.dto.request.UpsertImprovementPlanRequest;
import com.youhua.plan.dto.response.ImprovementPlanResponse;
import com.youhua.plan.entity.UserImprovementPlan;
import com.youhua.plan.mapper.UserImprovementPlanMapper;
import com.youhua.plan.service.ImprovementPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImprovementPlanServiceImpl implements ImprovementPlanService {

    private final UserImprovementPlanMapper planMapper;

    @Override
    public ImprovementPlanResponse upsertMyPlan(UpsertImprovementPlanRequest request) {
        Long userId = RequestContextUtil.getCurrentUserId();

        UserImprovementPlan plan = findByUserId(userId);

        if (plan == null) {
            plan = new UserImprovementPlan();
            plan.setUserId(userId);
            plan.setLayer1Completed(false);
            plan.setLayer2Completed(false);
            plan.setLayer3Completed(false);
        }

        applyRequest(plan, request);

        if (plan.getId() == null) {
            try {
                planMapper.insert(plan);
                log.debug("[ImprovementPlan] created userId={}", userId);
            } catch (DuplicateKeyException e) {
                // 并发竞态：另一个请求已抢先 INSERT，降级为查询后 UPDATE
                log.warn("[ImprovementPlan] concurrent insert conflict userId={}, fallback to update", userId);
                plan = findByUserId(userId);
                applyRequest(plan, request);
                planMapper.updateById(plan);
            }
        } else {
            planMapper.updateById(plan);
            log.debug("[ImprovementPlan] updated userId={} layer1={} layer2={} layer3={}",
                    userId, plan.getLayer1Completed(), plan.getLayer2Completed(), plan.getLayer3Completed());
        }

        return toResponse(plan);
    }

    @Override
    public ImprovementPlanResponse getMyPlan() {
        Long userId = RequestContextUtil.getCurrentUserId();
        UserImprovementPlan plan = findByUserId(userId);
        return plan != null ? toResponse(plan) : ImprovementPlanResponse.empty();
    }

    @Override
    public void deleteMyPlan() {
        Long userId = RequestContextUtil.getCurrentUserId();
        planMapper.delete(
                new LambdaQueryWrapper<UserImprovementPlan>()
                        .eq(UserImprovementPlan::getUserId, userId));
        log.info("[ImprovementPlan] deleted userId={}", userId);
    }

    // ----------------------------------------------------------------
    // private helpers
    // ----------------------------------------------------------------

    private void applyRequest(UserImprovementPlan plan, UpsertImprovementPlanRequest request) {
        // 仅覆盖非 null 字段，null 表示"不变"
        if (request.getLayer1Completed() != null) plan.setLayer1Completed(request.getLayer1Completed());
        if (request.getLayer1ReportId() != null)  plan.setLayer1ReportId(request.getLayer1ReportId());
        if (request.getLayer2Completed() != null) plan.setLayer2Completed(request.getLayer2Completed());
        if (request.getLayer3Completed() != null) plan.setLayer3Completed(request.getLayer3Completed());
    }

    private UserImprovementPlan findByUserId(Long userId) {
        return planMapper.selectOne(
                new LambdaQueryWrapper<UserImprovementPlan>()
                        .eq(UserImprovementPlan::getUserId, userId));
    }

    private ImprovementPlanResponse toResponse(UserImprovementPlan plan) {
        return ImprovementPlanResponse.builder()
                .layer1Completed(Boolean.TRUE.equals(plan.getLayer1Completed()))
                .layer1ReportId(plan.getLayer1ReportId())
                .layer2Completed(Boolean.TRUE.equals(plan.getLayer2Completed()))
                .layer3Completed(Boolean.TRUE.equals(plan.getLayer3Completed()))
                .updateTime(plan.getUpdateTime())
                .build();
    }
}
