package com.youhua.infra.log.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TraceIdFilterTest {

    private TraceIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @Test
    void should_use_client_trace_id_when_header_present() throws ServletException, IOException {
        String clientTraceId = "client-trace-id-12345";
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, clientTraceId);

        doAnswer(invocation -> {
            assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isEqualTo(clientTraceId);
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(clientTraceId);
        verify(chain).doFilter(request, response);
    }

    @Test
    void should_generate_trace_id_when_header_absent() throws ServletException, IOException {
        doAnswer(invocation -> {
            String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
            assertThat(traceId).isNotNull().hasSize(32);
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        String responseTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(responseTraceId).isNotNull().hasSize(32);
        verify(chain).doFilter(request, response);
    }

    @Test
    void should_set_response_header_when_filter_executes() throws ServletException, IOException {
        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isNotNull();
    }

    @Test
    void should_clear_mdc_when_request_completes() throws ServletException, IOException {
        filter.doFilter(request, response, chain);

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void should_clear_mdc_when_chain_throws_exception() throws ServletException, IOException {
        doThrow(new ServletException("test error")).when(chain).doFilter(request, response);

        try {
            filter.doFilter(request, response, chain);
        } catch (ServletException ignored) {
            // expected
        }

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }
}
