package com.youhua.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youhua.common.util.RequestContextUtil;
import com.youhua.plan.dto.request.UpsertImprovementPlanRequest;
import com.youhua.plan.dto.response.ImprovementPlanResponse;
import com.youhua.plan.entity.UserImprovementPlan;
import com.youhua.plan.mapper.UserImprovementPlanMapper;
import com.youhua.plan.service.impl.ImprovementPlanServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImprovementPlanServiceImpl 单元测试")
class ImprovementPlanServiceImplTest {

    @Mock
    private UserImprovementPlanMapper planMapper;

    @InjectMocks
    private ImprovementPlanServiceImpl planService;

    private MockedStatic<RequestContextUtil> mockedContext;
    private static final Long USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        mockedContext = mockStatic(RequestContextUtil.class);
        mockedContext.when(RequestContextUtil::getCurrentUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        mockedContext.close();
    }

    // ----------------------------------------------------------------
    // getMyPlan
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should_return_empty_defaults_when_no_plan_exists")
    void should_return_empty_defaults_when_no_plan_exists() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ImprovementPlanResponse result = planService.getMyPlan();

        assertThat(result.getLayer1Completed()).isFalse();
        assertThat(result.getLayer2Completed()).isFalse();
        assertThat(result.getLayer3Completed()).isFalse();
        assertThat(result.getLayer1ReportId()).isNull();
    }

    @Test
    @DisplayName("should_return_persisted_plan_when_plan_exists")
    void should_return_persisted_plan_when_plan_exists() {
        UserImprovementPlan plan = buildPlan(true, 42L, true, false);
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        ImprovementPlanResponse result = planService.getMyPlan();

        assertThat(result.getLayer1Completed()).isTrue();
        assertThat(result.getLayer1ReportId()).isEqualTo(42L);
        assertThat(result.getLayer2Completed()).isTrue();
        assertThat(result.getLayer3Completed()).isFalse();
    }

    // ----------------------------------------------------------------
    // upsertMyPlan — create path
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should_insert_new_plan_when_user_has_no_existing_plan")
    void should_insert_new_plan_when_user_has_no_existing_plan() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        UpsertImprovementPlanRequest req = request(true, 99L, false, false);

        planService.upsertMyPlan(req);

        ArgumentCaptor<UserImprovementPlan> captor = ArgumentCaptor.forClass(UserImprovementPlan.class);
        verify(planMapper).insert(captor.capture());
        verify(planMapper, never()).updateById(any(UserImprovementPlan.class));

        UserImprovementPlan inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(USER_ID);
        assertThat(inserted.getLayer1Completed()).isTrue();
        assertThat(inserted.getLayer1ReportId()).isEqualTo(99L);
        assertThat(inserted.getLayer2Completed()).isFalse();
    }

    @Test
    @DisplayName("should_update_existing_plan_when_user_already_has_plan")
    void should_update_existing_plan_when_user_already_has_plan() {
        UserImprovementPlan existing = buildPlan(true, 42L, false, false);
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        UpsertImprovementPlanRequest req = request(null, null, true, false);

        planService.upsertMyPlan(req);

        ArgumentCaptor<UserImprovementPlan> captor = ArgumentCaptor.forClass(UserImprovementPlan.class);
        verify(planMapper).updateById(captor.capture());
        verify(planMapper, never()).insert(any(UserImprovementPlan.class));

        UserImprovementPlan updated = captor.getValue();
        // layer1 unchanged (null in request → preserve existing)
        assertThat(updated.getLayer1Completed()).isTrue();
        assertThat(updated.getLayer1ReportId()).isEqualTo(42L);
        // layer2 updated
        assertThat(updated.getLayer2Completed()).isTrue();
    }

    @Test
    @DisplayName("should_preserve_null_fields_when_request_fields_are_null")
    void should_preserve_null_fields_when_request_fields_are_null() {
        UserImprovementPlan existing = buildPlan(true, 10L, true, false);
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        // 只设置 layer3，其他为 null
        UpsertImprovementPlanRequest req = new UpsertImprovementPlanRequest();
        req.setLayer3Completed(true);

        planService.upsertMyPlan(req);

        ArgumentCaptor<UserImprovementPlan> captor = ArgumentCaptor.forClass(UserImprovementPlan.class);
        verify(planMapper).updateById(captor.capture());

        UserImprovementPlan updated = captor.getValue();
        assertThat(updated.getLayer1Completed()).isTrue();   // preserved
        assertThat(updated.getLayer1ReportId()).isEqualTo(10L); // preserved
        assertThat(updated.getLayer2Completed()).isTrue();   // preserved
        assertThat(updated.getLayer3Completed()).isTrue();   // updated
    }

    @Test
    @DisplayName("should_return_full_response_after_upsert")
    void should_return_full_response_after_upsert() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        UpsertImprovementPlanRequest req = request(true, 7L, true, true);

        ImprovementPlanResponse result = planService.upsertMyPlan(req);

        assertThat(result.getLayer1Completed()).isTrue();
        assertThat(result.getLayer1ReportId()).isEqualTo(7L);
        assertThat(result.getLayer2Completed()).isTrue();
        assertThat(result.getLayer3Completed()).isTrue();
    }

    // ----------------------------------------------------------------
    // deleteMyPlan
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should_delete_plan_logically_when_delete_called")
    void should_delete_plan_logically_when_delete_called() {
        planService.deleteMyPlan();

        verify(planMapper).delete(any(LambdaQueryWrapper.class));
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    private UserImprovementPlan buildPlan(boolean l1, Long reportId, boolean l2, boolean l3) {
        UserImprovementPlan plan = new UserImprovementPlan();
        plan.setId(100L);
        plan.setUserId(USER_ID);
        plan.setLayer1Completed(l1);
        plan.setLayer1ReportId(reportId);
        plan.setLayer2Completed(l2);
        plan.setLayer3Completed(l3);
        plan.setUpdateTime(LocalDateTime.now());
        return plan;
    }

    private UpsertImprovementPlanRequest request(Boolean l1, Long reportId, Boolean l2, Boolean l3) {
        UpsertImprovementPlanRequest req = new UpsertImprovementPlanRequest();
        req.setLayer1Completed(l1);
        req.setLayer1ReportId(reportId);
        req.setLayer2Completed(l2);
        req.setLayer3Completed(l3);
        return req;
    }
}
