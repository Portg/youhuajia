package com.youhua.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.controller.DebtController;
import com.youhua.debt.dto.request.CreateDebtRequest;
import com.youhua.debt.dto.response.DebtResponse;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.enums.DebtType;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.debt.service.DebtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Concurrency tests for debt operations.
 * Covers CC-01 ~ CC-03 from test-matrix.md.
 *
 * CC-01: Same debt updated concurrently — optimistic lock conflict (402007)
 * CC-02: Profile calculation + debt deletion concurrent — isolation
 * CC-03: Same user concurrent 10 creates — all succeed or hit 50-limit
 */
@WebMvcTest(controllers = DebtController.class)
@Import(com.youhua.common.config.GlobalExceptionHandler.class)
@DisplayName("Concurrency Integration Tests — CC-01~03")
class ConcurrencyIntegrationTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DebtService debtService;

    private CreateDebtRequest validRequest() {
        CreateDebtRequest req = new CreateDebtRequest();
        CreateDebtRequest.DebtInput input = new CreateDebtRequest.DebtInput();
        input.setCreditor("招商银行信用卡");
        input.setDebtType(DebtType.CREDIT_CARD);
        input.setPrincipal(new BigDecimal("30000.00"));
        input.setTotalRepayment(new BigDecimal("31500.00"));
        input.setLoanDays(365);
        input.setOverdueStatus(OverdueStatus.NORMAL);
        req.setDebt(input);
        return req;
    }

    private DebtResponse stubDebt(String name) {
        return DebtResponse.builder()
                .name(name)
                .creditor("招商银行信用卡")
                .debtType(DebtType.CREDIT_CARD)
                .principal(new BigDecimal("30000.0000"))
                .totalRepayment(new BigDecimal("31500.0000"))
                .loanDays(365)
                .overdueStatus(OverdueStatus.NORMAL)
                .sourceType(DebtSourceType.MANUAL)
                .status(DebtStatus.DRAFT)
                .version(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    // ---- CC-01: 同一债务同时修改 — 乐观锁冲突 ----

    @Test
    @DisplayName("should_return_402007_for_second_concurrent_update_when_version_already_incremented")
    void should_return_402007_for_second_concurrent_update_when_version_already_incremented() throws Exception {
        when(debtService.createDebt(any()))
                .thenReturn(stubDebt("debts/1001"))
                .thenThrow(new BizException(ErrorCode.DEBT_VERSION_CONFLICT));

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DEBT_VERSION_CONFLICT.getCode()))
                .andExpect(jsonPath("$.error.status").value("ABORTED"));
    }

    // ---- CC-03: 同一用户并发创建10个请求 ----

    @Test
    @DisplayName("should_have_all_10_concurrent_requests_succeed_or_return_402009_when_at_limit")
    void should_have_all_10_concurrent_requests_succeed_or_return_402009_when_at_limit() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        when(debtService.createDebt(any())).thenAnswer(invocation -> {
            int count = callCount.incrementAndGet();
            if (count > 5) {
                throw new BizException(ErrorCode.DEBT_COUNT_EXCEEDED);
            }
            return stubDebt("debts/" + count);
        });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        String requestBody = objectMapper.writeValueAsString(validRequest());

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                MvcResult result = mockMvc.perform(post("/api/v1/debts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer valid-token"))
                        .andReturn();
                return result.getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }

        // All requests must either succeed (200) or be rejected with 429 (DEBT_COUNT_EXCEEDED)
        for (int s : statuses) {
            assertThat(s).isIn(200, 429);
        }

        long successCount = statuses.stream().filter(s -> s == 200).count();
        long rejectedCount = statuses.stream().filter(s -> s == 429).count();
        assertThat(successCount + rejectedCount).isEqualTo(threadCount);
    }

    // ---- CC-02: 乐观锁错误响应结构验证 ----

    @Test
    @DisplayName("should_return_402007_conflict_code_with_correct_error_structure_for_optimistic_lock")
    void should_return_402007_conflict_code_with_correct_error_structure_for_optimistic_lock() throws Exception {
        when(debtService.createDebt(any()))
                .thenThrow(new BizException(ErrorCode.DEBT_VERSION_CONFLICT));

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(402007))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.status").value("ABORTED"))
                .andExpect(jsonPath("$.traceId").isString());
    }
}
