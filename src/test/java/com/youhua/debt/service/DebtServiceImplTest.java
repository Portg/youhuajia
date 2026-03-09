package com.youhua.debt.service;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.dto.request.CreateDebtRequest;
import com.youhua.debt.dto.request.ListDebtsRequest;
import com.youhua.debt.dto.request.UpdateDebtRequest;
import com.youhua.debt.dto.response.DebtResponse;
import com.youhua.debt.dto.response.ListDebtsResponse;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.enums.DebtType;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.debt.service.impl.DebtServiceImpl;
import com.youhua.engine.apr.AprCalculator;
import com.youhua.engine.apr.AprConfig;
import com.youhua.infra.log.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DebtServiceImpl Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DebtServiceImplTest {

    @Mock
    private DebtMapper debtMapper;

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Captor
    private ArgumentCaptor<Debt> debtCaptor;


    private AprCalculator aprCalculator;
    private DebtServiceImpl debtService;

    private static final Long TEST_USER_ID = 100L;
    private static final Long TEST_DEBT_ID = 1001L;

    @BeforeEach
    void setUp() {
        AprConfig config = new AprConfig();
        config.setWarningThreshold(new BigDecimal("36.0"));
        config.setDangerThreshold(new BigDecimal("100.0"));
        config.setAbnormalThreshold(new BigDecimal("1000.0"));
        config.setMaxAllowed(new BigDecimal("10000.0"));
        aprCalculator = new AprCalculator(config);
        debtService = new DebtServiceImpl(debtMapper, aprCalculator, operationLogService, redisTemplate);

        // Set up request context with userId attribute
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setAttribute("userId", TEST_USER_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        // Default Redis mock setup
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Default summary mock for listDebts
        Map<String, Object> emptySummary = new HashMap<>();
        emptySummary.put("total_count", 0L);
        emptySummary.put("total_principal", BigDecimal.ZERO);
        emptySummary.put("total_monthly_payment", BigDecimal.ZERO);
        emptySummary.put("confirmed_count", 0L);
        when(debtMapper.selectSummaryByUserId(any())).thenReturn(emptySummary);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ===== createDebt Tests =====

    @Test
    @DisplayName("should_create_debt_when_valid_request")
    void should_create_debt_when_valid_request() {
        CreateDebtRequest request = buildCreateDebtRequest(null);

        DebtResponse response = debtService.createDebt(request);

        assertThat(response).isNotNull();
        assertThat(response.getCreditor()).isEqualTo("招商银行");
        assertThat(response.getStatus()).isEqualTo(DebtStatus.DRAFT);
        assertThat(response.getSourceType()).isEqualTo(DebtSourceType.MANUAL);
        // Verify debt was inserted
        verify(debtMapper).insert(debtCaptor.capture());
        Debt inserted = debtCaptor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(inserted.getStatus()).isEqualTo(DebtStatus.DRAFT);
    }

    @Test
    @DisplayName("should_check_idempotency_when_request_id_provided")
    void should_check_idempotency_when_request_id_provided() {
        CreateDebtRequest request = buildCreateDebtRequest("test-request-id-123");
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

        DebtResponse response = debtService.createDebt(request);

        assertThat(response).isNotNull();
        verify(valueOperations, times(1)).setIfAbsent(
                eq("debt:requestId:test-request-id-123"), eq("1"), anyLong(), any());
    }

    @Test
    @DisplayName("should_throw_when_duplicate_request_id_detected")
    void should_throw_when_duplicate_request_id_detected() {
        CreateDebtRequest request = buildCreateDebtRequest("duplicate-request-id");
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> debtService.createDebt(request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_REQUEST));
    }

    @Test
    @DisplayName("should_throw_when_no_authentication_context")
    void should_throw_when_no_authentication_context() {
        RequestContextHolder.resetRequestAttributes();
        CreateDebtRequest request = buildCreateDebtRequest(null);

        assertThatThrownBy(() -> debtService.createDebt(request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TOKEN_INVALID));
    }

    // ===== getDebt Tests =====

    @Test
    @DisplayName("should_return_debt_when_exists_and_owned_by_user")
    void should_return_debt_when_exists_and_owned_by_user() {
        Debt debt = buildDraftDebt();
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt);

        DebtResponse response = debtService.getDebt(TEST_DEBT_ID);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("debts/" + TEST_DEBT_ID);
        assertThat(response.getCreditor()).isEqualTo("招商银行");
    }

    @Test
    @DisplayName("should_throw_not_found_when_debt_does_not_exist")
    void should_throw_not_found_when_debt_does_not_exist() {
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(null);

        assertThatThrownBy(() -> debtService.getDebt(TEST_DEBT_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEBT_NOT_FOUND));
    }

    @Test
    @DisplayName("should_throw_not_found_when_debt_belongs_to_other_user")
    void should_throw_not_found_when_debt_belongs_to_other_user() {
        Debt debt = buildDraftDebt();
        debt.setUserId(999L);
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt);

        assertThatThrownBy(() -> debtService.getDebt(TEST_DEBT_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEBT_NOT_FOUND));
    }

    // ===== listDebts Tests =====

    @Test
    @DisplayName("should_return_empty_list_when_no_debts")
    void should_return_empty_list_when_no_debts() {
        when(debtMapper.selectList(any())).thenReturn(Collections.emptyList());

        ListDebtsRequest request = new ListDebtsRequest();
        ListDebtsResponse response = debtService.listDebts(request);

        assertThat(response.getDebts()).isEmpty();
        assertThat(response.getNextPageToken()).isNull();
        assertThat(response.getSummary().getTotalCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("should_return_debts_list_when_debts_exist")
    void should_return_debts_list_when_debts_exist() {
        Debt debt1 = buildDraftDebt();
        Debt debt2 = buildDraftDebt();
        debt2.setId(1002L);
        debt2.setCreditor("工商银行");
        List<Debt> debts = List.of(debt1, debt2);
        when(debtMapper.selectList(any())).thenReturn(debts);

        Map<String, Object> summary = new HashMap<>();
        summary.put("total_count", 2L);
        summary.put("total_principal", new BigDecimal("200000"));
        summary.put("total_monthly_payment", new BigDecimal("10000"));
        summary.put("confirmed_count", 0L);
        when(debtMapper.selectSummaryByUserId(TEST_USER_ID)).thenReturn(summary);

        ListDebtsRequest request = new ListDebtsRequest();
        ListDebtsResponse response = debtService.listDebts(request);

        assertThat(response.getDebts()).hasSize(2);
        assertThat(response.getSummary().getTotalCount()).isEqualTo(2);
    }

    // ===== updateDebt Tests =====

    @Test
    @DisplayName("should_update_creditor_when_updateMask_contains_creditor")
    void should_update_creditor_when_updateMask_contains_creditor() {
        Debt debt = buildDraftDebt();
        Debt updatedDebt = buildDraftDebt();
        updatedDebt.setCreditor("中国银行");
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt, updatedDebt);
        when(debtMapper.updateById(any(Debt.class))).thenReturn(1);

        UpdateDebtRequest request = new UpdateDebtRequest();
        DebtResponse debtInput = DebtResponse.builder().creditor("中国银行").build();
        request.setDebt(debtInput);
        request.setUpdateMask("creditor");

        DebtResponse response = debtService.updateDebt(TEST_DEBT_ID, request);

        verify(debtMapper, times(1)).updateById(any(Debt.class));
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("should_throw_state_invalid_when_debt_in_profile")
    void should_throw_state_invalid_when_debt_in_profile() {
        Debt debt = buildDraftDebt();
        debt.setStatus(DebtStatus.IN_PROFILE);
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt);

        UpdateDebtRequest request = new UpdateDebtRequest();
        request.setDebt(DebtResponse.builder().creditor("新债权人").build());
        request.setUpdateMask("creditor");

        assertThatThrownBy(() -> debtService.updateDebt(TEST_DEBT_ID, request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEBT_IN_PROFILE));
    }

    @Test
    @DisplayName("should_throw_version_conflict_when_optimistic_lock_fails")
    void should_throw_version_conflict_when_optimistic_lock_fails() {
        Debt debt = buildDraftDebt();
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt);
        when(debtMapper.updateById(any(Debt.class))).thenReturn(0);

        UpdateDebtRequest request = new UpdateDebtRequest();
        request.setDebt(DebtResponse.builder().creditor("新债权人").build());
        request.setUpdateMask("creditor");

        assertThatThrownBy(() -> debtService.updateDebt(TEST_DEBT_ID, request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEBT_VERSION_CONFLICT));
    }

    // ===== deleteDebt Tests =====

    @Test
    @DisplayName("should_delete_debt_when_status_is_draft")
    void should_delete_debt_when_status_is_draft() {
        Debt debt = buildDraftDebt();
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt);
        when(debtMapper.deleteById(anyLong())).thenReturn(1);

        debtService.deleteDebt(TEST_DEBT_ID);

        verify(debtMapper, times(1)).deleteById(TEST_DEBT_ID);
        // Verify operation log was saved
        verify(operationLogService).record(eq(TEST_USER_ID), any(), any(), eq("Debt"), eq(TEST_DEBT_ID), any());
    }

    @Test
    @DisplayName("should_throw_state_invalid_when_deleting_confirmed_debt")
    void should_throw_state_invalid_when_deleting_confirmed_debt() {
        Debt debt = buildDraftDebt();
        debt.setStatus(DebtStatus.CONFIRMED);
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt);

        assertThatThrownBy(() -> debtService.deleteDebt(TEST_DEBT_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEBT_STATE_INVALID));
    }

    // ===== confirmDebt Tests =====

    @Test
    @DisplayName("should_confirm_debt_and_calculate_apr_when_valid")
    void should_confirm_debt_and_calculate_apr_when_valid() {
        Debt debt = buildDraftDebt();
        Debt confirmedDebt = buildDraftDebt();
        confirmedDebt.setStatus(DebtStatus.CONFIRMED);
        confirmedDebt.setApr(new BigDecimal("60.833333"));
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt, confirmedDebt);
        when(debtMapper.updateById(any(Debt.class))).thenReturn(1);

        DebtResponse response = debtService.confirmDebt(TEST_DEBT_ID);

        verify(debtMapper, times(1)).updateById(debtCaptor.capture());
        Debt updated = debtCaptor.getValue();
        assertThat(updated.getStatus()).isEqualTo(DebtStatus.CONFIRMED);
        assertThat(updated.getApr()).isNotNull();
    }

    @Test
    @DisplayName("should_throw_state_invalid_when_confirming_already_confirmed_debt")
    void should_throw_state_invalid_when_confirming_already_confirmed_debt() {
        Debt debt = buildDraftDebt();
        debt.setStatus(DebtStatus.CONFIRMED);
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt);

        assertThatThrownBy(() -> debtService.confirmDebt(TEST_DEBT_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEBT_STATE_INVALID));
    }

    @Test
    @DisplayName("should_throw_confirm_missing_when_principal_is_zero")
    void should_throw_confirm_missing_when_principal_is_zero() {
        Debt debt = buildDraftDebt();
        debt.setPrincipal(BigDecimal.ZERO);
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt);

        assertThatThrownBy(() -> debtService.confirmDebt(TEST_DEBT_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEBT_CONFIRM_MISSING_FIELDS));
    }

    @Test
    @DisplayName("should_throw_confirm_missing_when_loan_days_is_zero")
    void should_throw_confirm_missing_when_loan_days_is_zero() {
        Debt debt = buildDraftDebt();
        debt.setLoanDays(0);
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt);

        assertThatThrownBy(() -> debtService.confirmDebt(TEST_DEBT_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEBT_CONFIRM_MISSING_FIELDS));
    }

    // ===== includeDebtInProfile Tests =====

    @Test
    @DisplayName("should_include_debt_in_profile_when_status_is_confirmed")
    void should_include_debt_in_profile_when_status_is_confirmed() {
        Debt debt = buildDraftDebt();
        debt.setStatus(DebtStatus.CONFIRMED);
        debt.setApr(new BigDecimal("20.000000"));
        Debt inProfileDebt = buildDraftDebt();
        inProfileDebt.setStatus(DebtStatus.IN_PROFILE);
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt, inProfileDebt);
        when(debtMapper.updateById(any(Debt.class))).thenReturn(1);

        debtService.includeDebtInProfile(TEST_DEBT_ID);

        verify(debtMapper, times(1)).updateById(debtCaptor.capture());
        assertThat(debtCaptor.getValue().getStatus()).isEqualTo(DebtStatus.IN_PROFILE);
    }

    @Test
    @DisplayName("should_throw_state_invalid_when_including_draft_debt_in_profile")
    void should_throw_state_invalid_when_including_draft_debt_in_profile() {
        Debt debt = buildDraftDebt();
        when(debtMapper.selectById(TEST_DEBT_ID)).thenReturn(debt);

        assertThatThrownBy(() -> debtService.includeDebtInProfile(TEST_DEBT_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEBT_STATE_INVALID));
    }

    // ===== Helper Methods =====

    private CreateDebtRequest buildCreateDebtRequest(String requestId) {
        CreateDebtRequest request = new CreateDebtRequest();
        request.setRequestId(requestId);

        CreateDebtRequest.DebtInput debtInput = new CreateDebtRequest.DebtInput();
        debtInput.setCreditor("招商银行");
        debtInput.setDebtType(DebtType.CREDIT_CARD);
        debtInput.setPrincipal(new BigDecimal("10000.00"));
        debtInput.setTotalRepayment(new BigDecimal("10500.00"));
        debtInput.setLoanDays(30);
        request.setDebt(debtInput);

        return request;
    }

    private Debt buildDraftDebt() {
        Debt debt = new Debt();
        debt.setId(TEST_DEBT_ID);
        debt.setUserId(TEST_USER_ID);
        debt.setCreditor("招商银行");
        debt.setDebtType(DebtType.CREDIT_CARD);
        debt.setPrincipal(new BigDecimal("10000.00"));
        debt.setTotalRepayment(new BigDecimal("10500.00"));
        debt.setLoanDays(30);
        debt.setStatus(DebtStatus.DRAFT);
        debt.setSourceType(DebtSourceType.MANUAL);
        debt.setVersion(0);
        debt.setCreateTime(LocalDateTime.now());
        debt.setUpdateTime(LocalDateTime.now());
        return debt;
    }
}
