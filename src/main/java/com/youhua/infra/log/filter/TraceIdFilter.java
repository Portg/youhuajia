package com.youhua.infra.log.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * traceId 链路追踪 Filter。
 * <ul>
 *   <li>优先读取请求头 X-Trace-Id</li>
 *   <li>未携带则生成 32 位 UUID（去横线）</li>
 *   <li>注入 MDC，响应头返回 X-Trace-Id</li>
 *   <li>finally 清理 MDC</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            String traceId = resolveTraceId((HttpServletRequest) request);
            MDC.put(TRACE_ID_MDC_KEY, traceId);

            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);

            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
