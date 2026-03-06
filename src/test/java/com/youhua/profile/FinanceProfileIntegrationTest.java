package com.youhua.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.integration.WebMvcTestSupport;
import com.youhua.profile.controller.FinanceProfileController;
import com.youhua.profile.dto.response.FinanceProfileResponse;
import com.youhua.profile.enums.RiskLevel;
import com.youhua.profile.service.FinanceProfileService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for finance profile calculation API.
 * Covers API-PC01 ~ API-PC04 from test-matrix.md.
 */
@WebMvcTest(controllers = FinanceProfileController.class)
@Import(com.youhua.common.config.GlobalExceptionHandler.class)
@DisplayName("FinanceProfileController Integration Tests — API-PC01~04")
class FinanceProfileIntegrationTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FinanceProfileService financeProfileService;

    private FinanceProfileResponse stubUserAProfile() {
        return FinanceProfileResponse.builder()
                .name("users/100001/finance-profile")
                .totalDebt(new BigDecimal("50000.0000"))
                .debtCount(3)
                .weightedApr(new BigDecimal("8.440000"))
                .monthlyPayment(new BigDecimal("3700.0000"))
                .monthlyIncome(new BigDecimal("15000.0000"))
                .debtIncomeRatio(new BigDecimal("0.246700"))
                .restructureScore(new BigDecimal("82.00"))
                .riskLevel(RiskLevel.LOW)
                .lastCalculateTime(LocalDateTime.now())
                .build();
    }

    // ---- API-PC01: 正常计算 ----

    @Test
    @DisplayName("should_return_200_with_profile_when_user_has_confirmed_debts_and_income")
    void should_return_200_with_profile_when_user_has_confirmed_debts_and_income() throws Exception {
        when(financeProfileService.calculateFinanceProfile()).thenReturn(stubUserAProfile());

        String responseBody = mockMvc.perform(post("/api/v1/finance-profiles/mine:calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDebt").isNumber())
                .andExpect(jsonPath("$.debtCount").value(3))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // F-01: BigDecimal precision — never use float/double
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(responseBody);
        BigDecimal totalDebt = new BigDecimal(node.get("totalDebt").asText());
        assertThat(totalDebt.compareTo(new BigDecimal("50000.0000"))).isEqualTo(0);

        BigDecimal weightedApr = new BigDecimal(node.get("weightedApr").asText());
        assertThat(weightedApr.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    // ---- API-PC02: 无确认债务 ----

    @Test
    @DisplayName("should_return_403001_when_no_confirmed_debts_exist")
    void should_return_403001_when_no_confirmed_debts_exist() throws Exception {
        when(financeProfileService.calculateFinanceProfile())
                .thenThrow(new BizException(ErrorCode.PROFILE_NO_CONFIRMED_DEBT));

        mockMvc.perform(post("/api/v1/finance-profiles/mine:calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.PROFILE_NO_CONFIRMED_DEBT.getCode()))
                .andExpect(jsonPath("$.error.status").value("FAILED_PRECONDITION"));
    }

    // ---- API-PC03: 无收入数据 (200 with WARN — debtIncomeRatio null) ----

    @Test
    @DisplayName("should_return_200_with_null_debtIncomeRatio_when_no_income_data")
    void should_return_200_with_null_debtIncomeRatio_when_no_income_data() throws Exception {
        // Profile calculates without income: debtIncomeRatio is null, no exception (WARN path)
        FinanceProfileResponse profileWithoutIncome = FinanceProfileResponse.builder()
                .name("users/100001/finance-profile")
                .totalDebt(new BigDecimal("50000.0000"))
                .debtCount(2)
                .weightedApr(new BigDecimal("8.440000"))
                .monthlyPayment(new BigDecimal("3700.0000"))
                .monthlyIncome(null)
                .debtIncomeRatio(null)
                .restructureScore(new BigDecimal("75.00"))
                .riskLevel(RiskLevel.LOW)
                .lastCalculateTime(LocalDateTime.now())
                .build();

        when(financeProfileService.calculateFinanceProfile()).thenReturn(profileWithoutIncome);

        mockMvc.perform(post("/api/v1/finance-profiles/mine:calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtCount").value(2))
                .andExpect(jsonPath("$.debtIncomeRatio").doesNotExist());
    }

    // ---- API-PC04: 频繁触发 (1小时内>10次) ----

    @Test
    @DisplayName("should_return_403003_when_calculation_triggered_more_than_10_times_per_hour")
    void should_return_403003_when_calculation_triggered_more_than_10_times_per_hour() throws Exception {
        when(financeProfileService.calculateFinanceProfile())
                .thenThrow(new BizException(ErrorCode.PROFILE_CALCULATING));

        mockMvc.perform(post("/api/v1/finance-profiles/mine:calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.PROFILE_CALCULATING.getCode()))
                .andExpect(jsonPath("$.traceId").isString());
    }
}
