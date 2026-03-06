package com.youhua.common.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

@Configuration
public class SecurityHeaderFilter {

    @Bean
    public FilterRegistrationBean<SecurityHeaderFilterImpl> securityHeaderFilterRegistration() {
        FilterRegistrationBean<SecurityHeaderFilterImpl> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SecurityHeaderFilterImpl());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    static class SecurityHeaderFilterImpl implements Filter {

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
                throws IOException, ServletException {

            HttpServletRequest request = (HttpServletRequest) servletRequest;
            HttpServletResponse response = (HttpServletResponse) servletResponse;

            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            String path = request.getRequestURI();
            if (path.startsWith("/api/")) {
                response.setHeader("Cache-Control", "no-store");
            }

            chain.doFilter(request, response);
        }
    }
}
