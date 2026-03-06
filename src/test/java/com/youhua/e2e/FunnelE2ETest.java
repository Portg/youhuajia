package com.youhua.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.auth.dto.response.LoginResponse;
import com.youhua.debt.dto.response.DebtResponse;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.enums.DebtType;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.engine.dto.response.AssessPressureResponse;
import com.youhua.engine.dto.response.AssessPressureResponse.PressureLevel;
import com.youhua.engine.dto.response.SimulateRateResponse;
import com.youhua.profile.dto.response.FinanceProfileResponse;
import com.youhua.profile.dto.response.ReportResponse;
import com.youhua.profile.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Funnel E2E tests covering two user journeys via the HTTP layer.
 *
 * Service implementations do not exist yet (MVP stage), so service calls are stubbed.
 * These tests validate the controller routing, request/response serialisation, and
 * F-11/F-13 compliance assertions on the response payloads.
 *
 * - User A (healthy, score >= 60): full optimisation path
 * - User C (high-risk, score < 60): partial path + improvement plan, F-13 compliance
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Funnel E2E Tests — Full User Journey")
class FunnelE2ETest extends E2ETestSupport {

    private static final String AUTH_HEADER_A = "Bearer test-token-user-a-e2e";
    private static final String AUTH_HEADER_C = "Bearer test-token-user-c-e2e";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void stubServices() {
        // Auth stubs
        when(authService.createSession(any())).thenReturn(LoginResponse.builder()
                .accessToken("stub-access-token")
                .refreshToken("stub-refresh-token")
                .expiresIn(3600)
                .userId("users/100001")
                .newUser(false)
                .build());

        // Debt stubs
        when(debtService.createDebt(any())).thenReturn(DebtResponse.builder()
                .name("debts/200001")
                .creditor("招商银行信用卡")
                .debtType(DebtType.CREDIT_CARD)
                .principal(new BigDecimal("30000.0000"))
                .totalRepayment(new BigDecimal("31500.0000"))
                .apr(new BigDecimal("4.98"))
                .loanDays(365)
                .overdueStatus(OverdueStatus.NORMAL)
                .status(DebtStatus.DRAFT)
                .createTime(LocalDateTime.now())
                .build());

        when(debtService.confirmDebt(anyLong())).thenReturn(DebtResponse.builder()
                .name("debts/200001")
                .creditor("招商银行信用卡")
                .status(DebtStatus.CONFIRMED)
                .apr(new BigDecimal("4.98"))
                .build());

        // Engine stubs — User A healthy ratio
        when(engineService.assessPressure(any())).thenReturn(AssessPressureResponse.builder()
                .pressureIndex(new BigDecimal("24.70"))
                .level(PressureLevel.HEALTHY)
                .ratio(new BigDecimal("0.2467"))
                .hint("您的债务压力较低，可以考虑优化利率结构")
                .build());

        when(engineService.simulateRate(any())).thenReturn(SimulateRateResponse.builder()
                .currentMonthlyPayment(new BigDecimal("4200.00"))
                .targetMonthlyPayment(new BigDecimal("3850.00"))
                .monthlySaving(new BigDecimal("350.00"))
                .threeYearSaving(new BigDecimal("12600.00"))
                .currentIncomeRatio(new BigDecimal("0.28"))
                .targetIncomeRatio(new BigDecimal("0.2567"))
                .build());

        // Profile stubs — User A healthy score
        when(financeProfileService.calculateFinanceProfile()).thenReturn(buildHealthyProfile());
        when(financeProfileService.getFinanceProfile()).thenReturn(buildHealthyProfile());

        // Report stubs
        when(reportService.generateReport()).thenReturn(ReportResponse.builder()
                .name("reports/300001")
                .profileSnapshot(buildHealthyProfile())
                .priorityList(List.of(
                        ReportResponse.PriorityItem.builder()
                                .rank(1).debt("debts/200001").creditor("招商银行信用卡")
                                .apr(new BigDecimal("4.98")).reason("优先偿还高息债务").build()
                ))
                .actionPlan(Map.of("week1", "收集债务合同", "week4", "提交优化申请"))
                .aiSummary("您的财务状况良好，建议通过利率置换降低综合成本")
                .riskWarnings(List.of("注意信用卡分期隐性费用"))
                .reportVersion(1)
                .createTime(LocalDateTime.now())
                .build());

        when(reportService.getReport(anyLong())).thenReturn(ReportResponse.builder()
                .name("reports/300001")
                .profileSnapshot(buildHealthyProfile())
                .priorityList(List.of())
                .actionPlan(Map.of("week1", "收集债务合同"))
                .aiSummary("您的财务状况良好，建议通过利率置换降低综合成本")
                .riskWarnings(List.of())
                .reportVersion(1)
                .createTime(LocalDateTime.now())
                .build());
    }

    // ----------------------------------------------------------------
    // User A — Healthy (score >= 60), full funnel
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should_complete_full_funnel_and_generate_report_when_user_A_healthy_profile")
    void should_complete_full_funnel_and_generate_report_when_user_A_healthy_profile() throws Exception {

        // Step a: Pressure assessment (no auth required)
        MvcResult pressureResult = mockMvc.perform(post("/api/v1/engine/pressure:assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "monthlyPayment", "3700.00",
                                "monthlyIncome", "15000.00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pressureIndex").exists())
                .andExpect(jsonPath("$.level").exists())
                .andExpect(jsonPath("$.ratio").exists())
                .andReturn();

        String pressureBody = pressureResult.getResponse().getContentAsString();
        assertThat(pressureBody).contains("ratio");
        // 3700 / 15000 = ~0.247 — well below danger threshold; stub returns HEALTHY
        assertThat(pressureBody).contains("HEALTHY");

        // Step b: SMS send
        mockMvc.perform(post("/api/v1/auth/sms:send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "13900000001"))))
                .andExpect(status().isOk());

        // Step b: Login (createSession)
        mockMvc.perform(post("/api/v1/auth/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", "13900000001",
                                "smsCode", "123456"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("stub-access-token"));

        // Step c: Create a debt
        mockMvc.perform(post("/api/v1/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER_A)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "debt", Map.of(
                                        "creditor", "招商银行信用卡",
                                        "debtType", DebtType.CREDIT_CARD.name(),
                                        "principal", "30000.00",
                                        "totalRepayment", "31500.00",
                                        "loanDays", 365,
                                        "overdueStatus", OverdueStatus.NORMAL.name()
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("debts/200001"));

        // Step d: Confirm the debt
        mockMvc.perform(post("/api/v1/debts/200001:confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Step e: Trigger finance profile calculation
        mockMvc.perform(post("/api/v1/finance-profiles/mine:calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restructureScore").exists());

        // Step f: Get finance profile — score >= 60 for healthy user
        MvcResult profileResult = mockMvc.perform(get("/api/v1/finance-profiles/mine")
                        .header("Authorization", AUTH_HEADER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restructureScore").exists())
                .andExpect(jsonPath("$.riskLevel").exists())
                .andExpect(jsonPath("$.totalDebt").exists())
                .andReturn();

        String profileBody = profileResult.getResponse().getContentAsString();
        assertThat(profileBody).contains("restructureScore");
        // Stub returns score 72.0, risk LOW — verify F-13 compliance (score >= 60)
        assertThat(profileBody).contains("72");
        assertThat(profileBody).contains("LOW");

        // Step g: Rate simulation
        mockMvc.perform(post("/api/v1/engine/rate:simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER_A)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentWeightedApr", "15.00",
                                "targetApr", "8.50",
                                "totalPrincipal", "50000.00",
                                "avgLoanDays", 365,
                                "monthlyIncome", "15000.00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMonthlyPayment").exists())
                .andExpect(jsonPath("$.targetMonthlyPayment").exists())
                .andExpect(jsonPath("$.monthlySaving").exists());

        // Step h: Generate report
        MvcResult reportGenResult = mockMvc.perform(post("/api/v1/reports:generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.profileSnapshot").exists())
                .andReturn();

        String reportBody = reportGenResult.getResponse().getContentAsString();
        assertThat(reportBody).contains("reports/300001");
        assertThat(reportBody).contains("aiSummary");

        // Step i: Get report by ID
        mockMvc.perform(get("/api/v1/reports/300001")
                        .header("Authorization", AUTH_HEADER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("reports/300001"))
                .andExpect(jsonPath("$.profileSnapshot").exists())
                .andExpect(jsonPath("$.priorityList").exists())
                .andExpect(jsonPath("$.actionPlan").exists());
    }

    // ----------------------------------------------------------------
    // User C — High-risk (score < 60), partial funnel, F-13 compliance
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should_not_walk_normal_optimization_path_but_still_generate_improvement_plan_when_user_C_high_risk")
    void should_not_walk_normal_optimization_path_but_still_generate_improvement_plan_when_user_C_high_risk()
            throws Exception {

        // Override stubs for high-risk User C
        when(engineService.assessPressure(any())).thenReturn(AssessPressureResponse.builder()
                .pressureIndex(new BigDecimal("154.17"))
                .level(PressureLevel.SEVERE)
                .ratio(new BigDecimal("1.5417"))
                .hint("您的债务压力极高，建议立即寻求专业咨询")
                .build());

        FinanceProfileResponse highRiskProfile = FinanceProfileResponse.builder()
                .name("users/100003/finance-profile")
                .totalDebt(new BigDecimal("190000.0000"))
                .debtCount(3)
                .weightedApr(new BigDecimal("38.50"))
                .monthlyPayment(new BigDecimal("18500.0000"))
                .monthlyIncome(new BigDecimal("12000.0000"))
                .debtIncomeRatio(new BigDecimal("1.5417"))
                .restructureScore(new BigDecimal("28.00"))
                .riskLevel(RiskLevel.CRITICAL)
                .lastCalculateTime(LocalDateTime.now())
                .build();

        when(financeProfileService.calculateFinanceProfile()).thenReturn(highRiskProfile);
        when(financeProfileService.getFinanceProfile()).thenReturn(highRiskProfile);

        when(reportService.generateReport()).thenReturn(ReportResponse.builder()
                .name("reports/300003")
                .profileSnapshot(highRiskProfile)
                .priorityList(List.of())
                .actionPlan(Map.of("week1", "整理债务清单", "week4", "咨询债务顾问"))
                .aiSummary("您当前的财务状况需要重点关注，建议先整理债务结构，逐步改善信用状况")
                .riskWarnings(List.of("存在逾期债务，需优先处理"))
                .reportVersion(1)
                .createTime(LocalDateTime.now())
                .build());

        // Step a: Pressure assessment (heavy pressure)
        MvcResult pressureResult = mockMvc.perform(post("/api/v1/engine/pressure:assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "monthlyPayment", "18500.00",
                                "monthlyIncome", "12000.00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").exists())
                .andReturn();

        // 18500/12000 > 1.0 — SEVERE level expected
        assertThat(pressureResult.getResponse().getContentAsString()).contains("SEVERE");

        // Step b: SMS + Login
        mockMvc.perform(post("/api/v1/auth/sms:send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "13900000003"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", "13900000003",
                                "smsCode", "123456"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        // Step c: Create 3 debts (high APR, some overdue)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/debts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", AUTH_HEADER_C)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "debt", Map.of(
                                            "creditor", "某网贷平台" + i,
                                            "debtType", DebtType.CONSUMER_LOAN.name(),
                                            "principal", "50000.00",
                                            "totalRepayment", "72000.00",
                                            "loanDays", 365,
                                            "overdueStatus", OverdueStatus.OVERDUE_60.name()
                                    )
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").exists());
        }

        // Step d: Confirm debts
        mockMvc.perform(post("/api/v1/debts/200001:confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER_C))
                .andExpect(status().isOk());

        // Step e: Trigger profile calculation
        mockMvc.perform(post("/api/v1/finance-profiles/mine:calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER_C))
                .andExpect(status().isOk());

        // Step f: Get profile — score < 60 for high-risk user
        MvcResult profileResult = mockMvc.perform(get("/api/v1/finance-profiles/mine")
                        .header("Authorization", AUTH_HEADER_C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restructureScore").exists())
                .andExpect(jsonPath("$.riskLevel").exists())
                .andReturn();

        String profileBody = profileResult.getResponse().getContentAsString();
        // F-13: score < 60 must not show "申请失败" / "不符合条件"
        assertThat(profileBody).contains("28");
        assertThat(profileBody).contains("CRITICAL");
        assertThat(profileBody).doesNotContain("申请失败");
        assertThat(profileBody).doesNotContain("不符合条件");

        // User C does NOT get the rate simulation step (not the optimisation path)
        // Verify: report can still be generated with improvement plan
        MvcResult reportResult = mockMvc.perform(post("/api/v1/reports:generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER_C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.profileSnapshot").exists())
                .andExpect(jsonPath("$.actionPlan").exists())
                .andReturn();

        String reportBody = reportResult.getResponse().getContentAsString();

        // F-13: AI summary must not contain rejection language
        assertThat(reportBody).doesNotContain("申请失败");
        assertThat(reportBody).doesNotContain("不符合条件");

        // F-11: No panic language
        assertThat(reportBody).doesNotContain("问题严重");
        assertThat(reportBody).doesNotContain("赶紧行动");
        assertThat(reportBody).doesNotContain("最后机会");
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private FinanceProfileResponse buildHealthyProfile() {
        return FinanceProfileResponse.builder()
                .name("users/100001/finance-profile")
                .totalDebt(new BigDecimal("50000.0000"))
                .debtCount(3)
                .weightedApr(new BigDecimal("12.30"))
                .monthlyPayment(new BigDecimal("3700.0000"))
                .monthlyIncome(new BigDecimal("15000.0000"))
                .debtIncomeRatio(new BigDecimal("0.2467"))
                .restructureScore(new BigDecimal("72.00"))
                .riskLevel(RiskLevel.LOW)
                .lastCalculateTime(LocalDateTime.now())
                .build();
    }
}
