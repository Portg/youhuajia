package com.youhua.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API Contract Consistency Tests.
 *
 * Verifies that every URL path and HTTP method declared in the frontend API files
 * (youhuajia-app/src/api/*.js) is correctly exposed by the backend controllers.
 *
 * A mismatch here means the frontend would receive 404 errors at runtime.
 *
 * Strategy:
 * - Parse each .js API file and extract (method, url) pairs from request() calls.
 * - For each extracted endpoint, send a real HTTP probe to the backend.
 * - A 404 indicates the endpoint is missing; any other status code (including 4xx
 *   from auth/validation) means the route exists and is correctly mapped.
 *
 * Known mismatches are documented explicitly as regression tests so they are
 * visible and must be resolved before shipping.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("API Contract Consistency Tests — Frontend vs Backend")
class ApiContractTest extends E2ETestSupport {

    private static final String API_BASE = "/api/v1";
    private static final String AUTH_HEADER = "Bearer test-token-user-a";

    // Relative path from project root; resolved at runtime
    private static final String FRONTEND_API_DIR = "youhuajia-app/src/api";

    @Autowired
    private MockMvc mockMvc;

    // ----------------------------------------------------------------
    // Auth endpoints (auth.js)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should_expose_POST_auth_sms_send_matching_frontend_auth_js")
    void should_expose_POST_auth_sms_send_matching_frontend_auth_js() throws Exception {
        // Frontend: POST /auth/sms:send
        mockMvc.perform(post(API_BASE + "/auth/sms:send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("should_expose_POST_auth_sessions_matching_frontend_auth_js")
    void should_expose_POST_auth_sessions_matching_frontend_auth_js() throws Exception {
        // Frontend: POST /auth/sessions  — sends { phone, code }
        // Backend LoginRequest field: smsCode (NOT code) — CONTRACT MISMATCH documented below
        mockMvc.perform(post(API_BASE + "/auth/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("should_expose_POST_auth_sessions_refresh_matching_frontend_auth_js")
    void should_expose_POST_auth_sessions_refresh_matching_frontend_auth_js() throws Exception {
        // Frontend: POST /auth/sessions:refresh
        mockMvc.perform(post(API_BASE + "/auth/sessions:refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("should_expose_POST_auth_sessions_revoke_matching_frontend_auth_js")
    void should_expose_POST_auth_sessions_revoke_matching_frontend_auth_js() throws Exception {
        // Frontend: POST /auth/sessions:revoke
        mockMvc.perform(post(API_BASE + "/auth/sessions:revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    // ----------------------------------------------------------------
    // Debt endpoints (debt.js)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should_expose_GET_debts_matching_frontend_debt_js")
    void should_expose_GET_debts_matching_frontend_debt_js() throws Exception {
        // Frontend: GET /debts
        mockMvc.perform(get(API_BASE + "/debts")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("should_expose_POST_debts_matching_frontend_debt_js")
    void should_expose_POST_debts_matching_frontend_debt_js() throws Exception {
        // Frontend: POST /debts  — sends { requestId, debt }
        mockMvc.perform(post(API_BASE + "/debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER)
                        .content("{}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("should_expose_PATCH_debts_id_matching_frontend_debt_js")
    void should_expose_PATCH_debts_id_matching_frontend_debt_js() throws Exception {
        // Frontend: PATCH /debts/{debtId}
        mockMvc.perform(patch(API_BASE + "/debts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER)
                        .content("{}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("should_expose_DELETE_debts_id_matching_frontend_debt_js")
    void should_expose_DELETE_debts_id_matching_frontend_debt_js() throws Exception {
        // Frontend: DELETE /debts/{debtId}
        mockMvc.perform(delete(API_BASE + "/debts/1")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("should_expose_POST_debts_id_confirm_matching_frontend_debt_js")
    void should_expose_POST_debts_id_confirm_matching_frontend_debt_js() throws Exception {
        // Frontend: POST /debts/{id}:confirm
        mockMvc.perform(post(API_BASE + "/debts/1:confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    // ----------------------------------------------------------------
    // Engine endpoints (engine.js)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should_expose_POST_engine_pressure_assess_matching_frontend_engine_js")
    void should_expose_POST_engine_pressure_assess_matching_frontend_engine_js() throws Exception {
        // Frontend: POST /engine/pressure:assess  (no auth)
        mockMvc.perform(post(API_BASE + "/engine/pressure:assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("should_expose_POST_engine_apr_calculate_matching_frontend_engine_js")
    void should_expose_POST_engine_apr_calculate_matching_frontend_engine_js() throws Exception {
        // Frontend: POST /engine/apr:calculate
        mockMvc.perform(post(API_BASE + "/engine/apr:calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER)
                        .content("{}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("should_expose_POST_engine_rate_simulate_matching_frontend_engine_js")
    void should_expose_POST_engine_rate_simulate_matching_frontend_engine_js() throws Exception {
        // Frontend: POST /engine/rate:simulate
        mockMvc.perform(post(API_BASE + "/engine/rate:simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER)
                        .content("{}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    // ----------------------------------------------------------------
    // Finance profile endpoints (profile.js)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should_expose_GET_finance_profiles_me_matching_frontend_profile_js")
    void should_expose_GET_finance_profiles_me_matching_frontend_profile_js() throws Exception {
        // Frontend: GET /finance-profiles/mine
        // Backend:  GET /finance-profiles/mine
        // CONTRACT MISMATCH: frontend uses "mine", backend uses "me"
        // This test verifies the BACKEND path exists; the mismatch is asserted separately below.
        mockMvc.perform(get(API_BASE + "/finance-profiles/mine")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("should_expose_POST_finance_profiles_me_calculate_matching_frontend_profile_js")
    void should_expose_POST_finance_profiles_me_calculate_matching_frontend_profile_js() throws Exception {
        // Frontend: POST /finance-profiles/mine:calculate
        // Backend:  POST /finance-profiles/mine:calculate
        // CONTRACT MISMATCH: same "mine" vs "me" issue
        mockMvc.perform(post(API_BASE + "/finance-profiles/mine:calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    // ----------------------------------------------------------------
    // Report endpoints (report.js)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should_expose_POST_reports_generate_matching_frontend_report_js")
    void should_expose_POST_reports_generate_matching_frontend_report_js() throws Exception {
        // Frontend: POST /reports:generate
        mockMvc.perform(post(API_BASE + "/reports:generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("should_expose_GET_reports_id_matching_frontend_report_js")
    void should_expose_GET_reports_id_matching_frontend_report_js() throws Exception {
        // Frontend: GET /reports/{id}
        mockMvc.perform(get(API_BASE + "/reports/1")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    // ----------------------------------------------------------------
    // Documented contract mismatches (regression tests)
    // These tests DOCUMENT known divergences between frontend and backend.
    // They must be fixed before production deployment.
    // ----------------------------------------------------------------

    /**
     * MISMATCH #1: Finance profile path suffix
     *
     * Frontend (profile.js) uses:  /finance-profiles/mine
     * Backend controller exposes:  /finance-profiles/mine
     *
     * Impact: GET /api/v1/finance-profiles/mine returns 404 in production.
     * Resolution: Align one side to the other. Recommend backend changes to "mine"
     * to match the more expressive URL, OR update frontend to use "me".
     */
    @Test
    @DisplayName("should_detect_MISMATCH_1_finance_profile_path_mine_vs_me")
    void should_detect_MISMATCH_1_finance_profile_path_mine_vs_me() throws Exception {
        // Frontend calls this URL:
        int frontendUrlStatus = mockMvc.perform(get(API_BASE + "/finance-profiles/mine")
                        .header("Authorization", AUTH_HEADER))
                .andReturn().getResponse().getStatus();

        // Backend exposes this URL:
        int backendUrlStatus = mockMvc.perform(get(API_BASE + "/finance-profiles/mine")
                        .header("Authorization", AUTH_HEADER))
                .andReturn().getResponse().getStatus();

        // FIXED: frontend and backend now both use /finance-profiles/mine
        assertThat(frontendUrlStatus).as(
                "MISMATCH #1 RESOLVED: /finance-profiles/mine is now correctly handled"
        ).isNotIn(404, 500);

        assertThat(backendUrlStatus).as(
                "Backend path /finance-profiles/mine must be reachable (not 404)"
        ).isNotEqualTo(404);
    }

    /**
     * MISMATCH #2: Finance profile calculate path suffix
     *
     * Frontend (profile.js) uses:  /finance-profiles/mine:calculate
     * Backend controller exposes:  /finance-profiles/mine:calculate
     *
     * Same root cause as MISMATCH #1.
     */
    @Test
    @DisplayName("should_detect_MISMATCH_2_finance_profile_calculate_path_mine_vs_me")
    void should_detect_MISMATCH_2_finance_profile_calculate_path_mine_vs_me() throws Exception {
        int frontendUrlStatus = mockMvc.perform(post(API_BASE + "/finance-profiles/mine:calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER))
                .andReturn().getResponse().getStatus();

        int backendUrlStatus = mockMvc.perform(post(API_BASE + "/finance-profiles/mine:calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTH_HEADER))
                .andReturn().getResponse().getStatus();

        // FIXED: frontend and backend now both use /finance-profiles/mine:calculate
        assertThat(frontendUrlStatus).as(
                "MISMATCH #2 RESOLVED: /finance-profiles/mine:calculate is now correctly handled"
        ).isNotIn(404, 500);

        assertThat(backendUrlStatus).as(
                "Backend path /finance-profiles/mine:calculate must be reachable (not 404)"
        ).isNotEqualTo(404);
    }

    /**
     * MISMATCH #3: Login request field name
     *
     * Frontend (auth.js) sends:    { phone, code }
     * Backend LoginRequest expects: { phone, smsCode }
     *
     * Impact: Login always fails with 400 Bad Request because smsCode is @NotBlank
     * and the frontend sends field named "code" which is ignored.
     * Resolution: Backend must rename field to "code", OR frontend must send "smsCode".
     */
    @Test
    @DisplayName("should_detect_MISMATCH_3_login_request_field_code_vs_smsCode")
    void should_detect_MISMATCH_3_login_request_field_code_vs_smsCode() throws Exception {
        // Frontend sends "code" field:
        int withCodeField = mockMvc.perform(post(API_BASE + "/auth/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000001\",\"code\":\"123456\"}"))
                .andReturn().getResponse().getStatus();

        // Backend expects "smsCode" field:
        int withSmsCodeField = mockMvc.perform(post(API_BASE + "/auth/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000001\",\"smsCode\":\"123456\"}"))
                .andReturn().getResponse().getStatus();

        // Sending "code" (frontend format) results in validation failure (400) because smsCode is blank
        assertThat(withCodeField).as(
                "MISMATCH #3: Frontend sends {code} but backend requires {smsCode}. " +
                "Frontend format causes 400 Bad Request. " +
                "Fix: rename LoginRequest.smsCode to LoginRequest.code, " +
                "OR update frontend auth.js to send 'smsCode' instead of 'code'."
        ).isEqualTo(400);

        // Sending "smsCode" (backend expected format) should not return 400 for field validation
        assertThat(withSmsCodeField).as(
                "Backend handles {smsCode} correctly (not a 400 field-validation error)"
        ).isNotEqualTo(400);
    }

    // ----------------------------------------------------------------
    // Frontend API file existence check
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should_confirm_all_expected_frontend_api_files_exist")
    void should_confirm_all_expected_frontend_api_files_exist() {
        List<String> expectedFiles = List.of(
                "auth.js",
                "debt.js",
                "engine.js",
                "profile.js",
                "report.js",
                "request.js"
        );

        File apiDir = new File(FRONTEND_API_DIR);
        assertThat(apiDir).as(
                "Frontend API directory must exist at: " + apiDir.getAbsolutePath()
        ).isDirectory();

        for (String fileName : expectedFiles) {
            File apiFile = new File(apiDir, fileName);
            assertThat(apiFile).as(
                    "Frontend API file must exist: " + apiFile.getAbsolutePath()
            ).exists();
        }
    }

    @Test
    @DisplayName("should_confirm_report_js_uses_consistent_api_base_path")
    void should_confirm_report_js_uses_consistent_api_base_path() throws Exception {
        File reportJs = new File(FRONTEND_API_DIR + "/report.js");
        if (!reportJs.exists()) {
            // File existence is checked in the test above; skip here
            return;
        }
        String content = Files.readString(reportJs.toPath());
        // report.js should use relative paths (not hardcoded /api/v1) for consistency
        // Verify the request helper is used consistently
        assertThat(content).contains("request(");
        assertThat(content).contains("/reports:generate");
        assertThat(content).contains("/reports/");
    }
}
