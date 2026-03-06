package com.youhua.profile.service;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.infra.log.OperationLogService;
import com.youhua.profile.dto.request.BatchCreateIncomesRequest;
import com.youhua.profile.dto.response.IncomeResponse;
import com.youhua.profile.entity.IncomeRecord;
import com.youhua.profile.enums.IncomeType;
import com.youhua.profile.enums.VerificationStatus;
import com.youhua.profile.mapper.IncomeRecordMapper;
import com.youhua.profile.service.impl.IncomeServiceImpl;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IncomeServiceImpl Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncomeServiceImplTest {

    @Mock private IncomeRecordMapper incomeRecordMapper;
    @Mock private OperationLogService operationLogService;

    @Captor private ArgumentCaptor<IncomeRecord> incomeCaptor;

    private IncomeServiceImpl service;

    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new IncomeServiceImpl(incomeRecordMapper, operationLogService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", TEST_USER_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(incomeRecordMapper.insert((IncomeRecord) any(IncomeRecord.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("should_createAllIncomes_when_validBatchRequest")
    void should_createAllIncomes_when_validBatchRequest() {
        BatchCreateIncomesRequest request = buildRequest(
                new BigDecimal("10000"), IncomeType.SALARY, true,
                new BigDecimal("5000"), IncomeType.FREELANCE, false
        );

        List<IncomeResponse> responses = service.batchCreateIncomes(request);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getIncomeType()).isEqualTo(IncomeType.SALARY);
        assertThat(responses.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(responses.get(0).getPrimary()).isTrue();
        assertThat(responses.get(1).getIncomeType()).isEqualTo(IncomeType.FREELANCE);

        verify(incomeRecordMapper, times(2)).insert((IncomeRecord) incomeCaptor.capture());
        List<IncomeRecord> inserted = incomeCaptor.getAllValues();
        assertThat(inserted.get(0).getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(inserted.get(1).getUserId()).isEqualTo(TEST_USER_ID);
        verify(operationLogService, times(2)).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should_throwBizException_when_amountIsNegative")
    void should_throwBizException_when_amountIsNegative() {
        BatchCreateIncomesRequest request = new BatchCreateIncomesRequest();
        BatchCreateIncomesRequest.IncomeInput input = new BatchCreateIncomesRequest.IncomeInput();
        input.setIncomeType(IncomeType.SALARY);
        input.setAmount(new BigDecimal("-100"));
        input.setPrimary(true);
        request.setIncomes(List.of(input));

        assertThatThrownBy(() -> service.batchCreateIncomes(request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.INCOME_AMOUNT_INVALID);

        verify(incomeRecordMapper, times(0)).insert((IncomeRecord) any(IncomeRecord.class));
    }

    @Test
    @DisplayName("should_acceptZeroAmount_when_amountIsZero")
    void should_acceptZeroAmount_when_amountIsZero() {
        BatchCreateIncomesRequest request = new BatchCreateIncomesRequest();
        BatchCreateIncomesRequest.IncomeInput input = new BatchCreateIncomesRequest.IncomeInput();
        input.setIncomeType(IncomeType.INVESTMENT);
        input.setAmount(BigDecimal.ZERO);
        input.setPrimary(false);
        request.setIncomes(List.of(input));

        List<IncomeResponse> responses = service.batchCreateIncomes(request);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should_setVerificationStatusUnverified_when_creatingIncome")
    void should_setVerificationStatusUnverified_when_creatingIncome() {
        BatchCreateIncomesRequest request = buildRequest(
                new BigDecimal("8000"), IncomeType.BUSINESS, true
        );

        service.batchCreateIncomes(request);

        verify(incomeRecordMapper).insert((IncomeRecord) incomeCaptor.capture());
        IncomeRecord saved = incomeCaptor.getValue();
        assertThat(saved.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
    }

    // ===================== Helpers =====================

    private BatchCreateIncomesRequest buildRequest(Object... args) {
        BatchCreateIncomesRequest request = new BatchCreateIncomesRequest();
        List<BatchCreateIncomesRequest.IncomeInput> inputs = new ArrayList<>();

        for (int i = 0; i < args.length; i += 3) {
            BatchCreateIncomesRequest.IncomeInput input = new BatchCreateIncomesRequest.IncomeInput();
            input.setAmount((BigDecimal) args[i]);
            input.setIncomeType((IncomeType) args[i + 1]);
            input.setPrimary((Boolean) args[i + 2]);
            inputs.add(input);
        }
        request.setIncomes(inputs);
        return request;
    }
}
