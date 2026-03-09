package com.youhua.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.auth.service.AuthService;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private AuthService authService;

    private JwtAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(authService, new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    // ============================================================
    // TC-01: 回归测试 — sessions:revoke 必须要求认证
    // ============================================================

    @Test
    void should_return_401_when_revoke_session_called_without_token() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/sessions:revoke");
        request.setMethod("POST");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(String.valueOf(ErrorCode.TOKEN_INVALID.getCode()));
        verifyNoInteractions(chain);
        verifyNoInteractions(authService);
    }

    // ============================================================
    // TC-02~06: 公开路径无需 Token，直接放行
    // ============================================================

    @ParameterizedTest(name = "public path: {0}")
    @ValueSource(strings = {
            "/api/v1/auth/sms:send",
            "/api/v1/auth/sessions",
            "/api/v1/auth/sessions:refresh",
            "/api/v1/engine/pressure:assess",
            "/api/v1/engine/apr:calculate"
    })
    void should_pass_through_when_public_exact_path_without_token(String path)
            throws ServletException, IOException {
        request.setRequestURI(path);
        request.setMethod("POST");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(authService);
    }

    // ============================================================
    // TC-07~09: 基础设施前缀路径放行（Swagger / Actuator 等）
    // ============================================================

    @ParameterizedTest(name = "infra prefix path: {0}")
    @ValueSource(strings = {
            "/swagger-ui/index.html",
            "/api-docs/openapi.json",
            "/actuator/health"
    })
    void should_pass_through_when_infra_prefix_path(String path)
            throws ServletException, IOException {
        request.setRequestURI(path);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(authService);
    }

    // ============================================================
    // TC-10: 有效 Token — 放行并将 userId 注入 request attribute
    // ============================================================

    @Test
    void should_set_user_id_and_pass_through_when_valid_token() throws ServletException, IOException {
        request.setRequestURI("/api/v1/debts");
        request.addHeader("Authorization", "Bearer valid-token");
        when(authService.verifyJwtAndGetUserId("valid-token")).thenReturn(42L);

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute("userId")).isEqualTo(42L);
        verify(chain).doFilter(request, response);
    }

    // ============================================================
    // TC-11: Token 已过期 — 返回 401 TOKEN_EXPIRED
    // ============================================================

    @Test
    void should_return_401_token_expired_when_token_is_expired() throws ServletException, IOException {
        request.setRequestURI("/api/v1/debts");
        request.addHeader("Authorization", "Bearer expired-token");
        when(authService.verifyJwtAndGetUserId("expired-token"))
                .thenThrow(new BizException(ErrorCode.TOKEN_EXPIRED));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(String.valueOf(ErrorCode.TOKEN_EXPIRED.getCode()));
        verifyNoInteractions(chain);
    }

    // ============================================================
    // TC-12: Token 签名被篡改 — 返回 401 TOKEN_INVALID
    // ============================================================

    @Test
    void should_return_401_token_invalid_when_signature_tampered() throws ServletException, IOException {
        request.setRequestURI("/api/v1/debts");
        request.addHeader("Authorization", "Bearer tampered.token.here");
        when(authService.verifyJwtAndGetUserId("tampered.token.here"))
                .thenThrow(new BizException(ErrorCode.TOKEN_INVALID));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(String.valueOf(ErrorCode.TOKEN_INVALID.getCode()));
        verifyNoInteractions(chain);
    }

    // ============================================================
    // TC-13: Authorization header 缺失 — 返回 401
    // ============================================================

    @Test
    void should_return_401_when_authorization_header_missing() throws ServletException, IOException {
        request.setRequestURI("/api/v1/debts");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
        verifyNoInteractions(authService);
    }

    // ============================================================
    // TC-14: Authorization header 无 Bearer 前缀 — 返回 401
    // ============================================================

    @Test
    void should_return_401_when_bearer_prefix_missing() throws ServletException, IOException {
        request.setRequestURI("/api/v1/debts");
        request.addHeader("Authorization", "Basic c29tZXRva2Vu");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
        verifyNoInteractions(authService);
    }

    // ============================================================
    // TC-15: Bearer 后 token 为空字符串 — 返回 401
    // ============================================================

    @Test
    void should_return_401_when_bearer_token_is_blank() throws ServletException, IOException {
        request.setRequestURI("/api/v1/debts");
        request.addHeader("Authorization", "Bearer   ");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
        verifyNoInteractions(authService);
    }
}
