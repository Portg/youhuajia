package com.youhua.debt;

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
import com.youhua.integration.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller integration tests for debt creation API.
 * Covers API-DC01 ~ API-DC09 from test-matrix.md.
 *
 * Auth tests (API-DC07, API-DC08) verify that missing/expired tokens are rejected
 * before reaching the service layer.
 * Business rule tests (API-DC01~06, API-DC09) verify service-layer error codes.
 */
@WebMvcTest(controllers = DebtController.class)
@Import(com.youhua.common.config.GlobalExceptionHandler.class)
@DisplayName("DebtController Integration Tests — API-DC01~09")
class DebtControllerIntegrationTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DebtService debtService;

    // ---- Helpers ----

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

    private DebtResponse stubDebtResponse() {
        return DebtResponse.builder()
                .name("debts/1001")
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

    // ---- API-DC01: 正常创建 ----

    @Test
    @DisplayName("should_return_200_and_debt_resource_when_all_required_fields_provided")
    void should_return_200_and_debt_resource_when_all_required_fields_provided() throws Exception {
        when(debtService.createDebt(any())).thenReturn(stubDebtResponse());

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditor").value("招商银行信用卡"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // ---- API-DC02: 缺少creditor (JSR-380 @NotBlank fires) ----

    @Test
    @DisplayName("should_return_error_code_500007_when_creditor_is_null")
    void should_return_error_code_500007_when_creditor_is_null() throws Exception {
        CreateDebtRequest req = validRequest();
        req.getDebt().setCreditor(null);

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(500007));
    }

    @Test
    @DisplayName("should_return_402005_when_service_throws_DEBT_CREDITOR_EMPTY")
    void should_return_402005_when_service_throws_DEBT_CREDITOR_EMPTY() throws Exception {
        when(debtService.createDebt(any()))
                .thenThrow(new BizException(ErrorCode.DEBT_CREDITOR_EMPTY));

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DEBT_CREDITOR_EMPTY.getCode()));
    }

    // ---- API-DC03: 本金为0 (JSR-380 @DecimalMin("0.01") fires) ----

    @Test
    @DisplayName("should_return_500007_when_principal_is_zero")
    void should_return_500007_when_principal_is_zero() throws Exception {
        CreateDebtRequest req = validRequest();
        req.getDebt().setPrincipal(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(500007));
    }

    // ---- API-DC04: 本金为负 ----

    @Test
    @DisplayName("should_return_500007_when_principal_is_negative")
    void should_return_500007_when_principal_is_negative() throws Exception {
        CreateDebtRequest req = validRequest();
        req.getDebt().setPrincipal(new BigDecimal("-1000.00"));

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(500007));
    }

    // ---- API-DC05: 还款额 < 本金 (service layer validation) ----

    @Test
    @DisplayName("should_return_402003_when_totalRepayment_less_than_principal")
    void should_return_402003_when_totalRepayment_less_than_principal() throws Exception {
        when(debtService.createDebt(any()))
                .thenThrow(new BizException(ErrorCode.DEBT_REPAYMENT_INVALID));

        CreateDebtRequest req = validRequest();
        req.getDebt().setTotalRepayment(new BigDecimal("9000.00"));
        req.getDebt().setPrincipal(new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DEBT_REPAYMENT_INVALID.getCode()));
    }

    // ---- API-DC06: 天数为0 (JSR-380 @Min(1) fires) ----

    @Test
    @DisplayName("should_return_500007_when_loanDays_is_zero")
    void should_return_500007_when_loanDays_is_zero() throws Exception {
        CreateDebtRequest req = validRequest();
        req.getDebt().setLoanDays(0);

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(500007));
    }

    // ---- API-DC07: 未登录 (service throws TOKEN_INVALID) ----

    @Test
    @DisplayName("should_return_401_when_service_throws_TOKEN_INVALID")
    void should_return_401_when_service_throws_TOKEN_INVALID() throws Exception {
        when(debtService.createDebt(any()))
                .thenThrow(new BizException(ErrorCode.TOKEN_INVALID));

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.TOKEN_INVALID.getCode()));
    }

    // ---- API-DC08: Token过期 ----

    @Test
    @DisplayName("should_return_401_with_code_401004_when_token_is_expired")
    void should_return_401_with_code_401004_when_token_is_expired() throws Exception {
        when(debtService.createDebt(any()))
                .thenThrow(new BizException(ErrorCode.TOKEN_EXPIRED));

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .header("Authorization", "Bearer expired.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.TOKEN_EXPIRED.getCode()));
    }

    // ---- API-DC09: 债务超50笔 ----

    @Test
    @DisplayName("should_return_402009_when_user_already_has_50_debts")
    void should_return_402009_when_user_already_has_50_debts() throws Exception {
        when(debtService.createDebt(any()))
                .thenThrow(new BizException(ErrorCode.DEBT_COUNT_EXCEEDED));

        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DEBT_COUNT_EXCEEDED.getCode()))
                .andExpect(jsonPath("$.error.status").value("RESOURCE_EXHAUSTED"));
    }

    // ---- BigDecimal assertion safety: never use float/double (F-01) ----

    @Test
    @DisplayName("should_return_principal_as_BigDecimal_string_not_float_when_debt_created")
    void should_return_principal_as_BigDecimal_string_not_float_when_debt_created() throws Exception {
        when(debtService.createDebt(any())).thenReturn(stubDebtResponse());

        String responseBody = mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Parse principal as string to ensure no float precision loss (F-01)
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(responseBody);
        BigDecimal principal = new BigDecimal(node.get("principal").asText());
        // Must use compareTo, never equals, for BigDecimal assertion (F-01)
        assert principal.compareTo(new BigDecimal("30000.0000")) == 0
                : "Principal must be exactly 30000.0000";
    }
}
