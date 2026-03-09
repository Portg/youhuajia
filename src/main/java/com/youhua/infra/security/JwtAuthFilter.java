package com.youhua.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.auth.service.AuthService;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.common.response.ErrorResponse;
import com.youhua.infra.log.filter.TraceIdFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Set;

/**
 * JWT authentication filter.
 * Extracts Bearer token from Authorization header, validates it,
 * and sets userId as a request attribute for downstream services.
 *
 * <p>Registered via {@link SecurityFilterConfig}, not as @Component,
 * to avoid auto-detection by @WebMvcTest.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter implements Filter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api-docs",
            "/swagger-ui",
            "/actuator",
            "/favicon.ico"
    );

    // Exact-match public paths (no auth required)
    private static final Set<String> PUBLIC_EXACT_PATHS = Set.of(
            "/api/v1/auth/sms:send",
            "/api/v1/auth/sessions",
            "/api/v1/auth/sessions:refresh",
            "/api/v1/engine/pressure:assess",
            "/api/v1/engine/apr:calculate"
    );

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // Skip public endpoints
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeErrorResponse(response, ErrorCode.TOKEN_INVALID);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeErrorResponse(response, ErrorCode.TOKEN_INVALID);
            return;
        }

        try {
            Long userId = authService.verifyJwtAndGetUserId(token);
            request.setAttribute("userId", userId);
            chain.doFilter(request, response);
        } catch (BizException e) {
            writeErrorResponse(response, e.getErrorCode());
        }
    }

    private boolean isPublicPath(String path) {
        if (PUBLIC_EXACT_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getDefaultMessage())
                        .status(errorCode.getStatus())
                        .build())
                .traceId(traceId)
                .build();

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
