package com.youhua.infra.log.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 慢请求监控切面，切 @RestController 类的所有方法。
 * 超过阈值输出 WARN [SLOW_REQUEST]，正常输出 DEBUG [PERF]。
 */
@Slf4j
@Aspect
@Component
public class PerformanceLogAspect {

    @Value("${youhua.log.slow-request-threshold-ms:3000}")
    private long slowRequestThresholdMs;

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            String method = joinPoint.getSignature().toShortString();
            if (elapsed >= slowRequestThresholdMs) {
                log.warn("[SLOW_REQUEST] {} took {}ms (threshold={}ms)", method, elapsed, slowRequestThresholdMs);
            } else {
                log.debug("[PERF] {} took {}ms", method, elapsed);
            }
        }
    }
}
