package com.youhua.infra.resilience;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AOP aspect implementing the unified resilience chain: Timeout → Retry → CircuitBreaker → call.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>{@link BizException} is always treated as circuit breaker SUCCESS and is never retried.</li>
 *   <li>{@link CallNotPermittedException} is converted to {@code BizException(AI_UNAVAILABLE)}.</li>
 *   <li>Only exceptions listed in {@link RetrySpec#retryOn()} trigger retries.</li>
 *   <li>Timeout covers the entire execution including all retries.</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class ResilientAspect {

    private final ApplicationContext applicationContext;

    public ResilientAspect(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Around("@annotation(resilient)")
    public Object around(ProceedingJoinPoint pjp, Resilient resilient) throws Throwable {
        int timeoutSeconds = resilient.timeout().seconds();

        if (timeoutSeconds > 0) {
            return executeWithTimeout(pjp, resilient, timeoutSeconds);
        }
        return executeWithRetryAndCb(pjp, resilient);
    }

    private Object executeWithTimeout(ProceedingJoinPoint pjp, Resilient resilient, int timeoutSeconds) throws Throwable {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<Object> future = executor.submit(() -> {
            try {
                return executeWithRetryAndCb(pjp, resilient);
            } catch (Throwable t) {
                if (t instanceof Exception ex) throw ex;
                throw new BizException(ErrorCode.SYSTEM_BUSY, "系统异常: " + t.getMessage());
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            String method = pjp.getSignature().toShortString();
            log.error("[Resilient] Timeout after {}s for {}", timeoutSeconds, method);
            throw new com.youhua.ai.exception.AiTimeoutException("调用超时（" + timeoutSeconds + "s）: " + method);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) throw cause;
            throw e;
        } finally {
            executor.close();
        }
    }

    private Object executeWithRetryAndCb(ProceedingJoinPoint pjp, Resilient resilient) throws Throwable {
        int maxAttempts = resilient.retry().maxAttempts();
        Class<? extends Throwable>[] retryOn = resilient.retry().retryOn();

        Throwable lastException = null;
        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            try {
                return executeWithCircuitBreaker(pjp, resilient);
            } catch (BizException e) {
                // BizException is never retried
                throw e;
            } catch (Throwable t) {
                if (isRetryable(t, retryOn) && attempt < maxAttempts) {
                    lastException = t;
                    log.warn("[Resilient] Retryable exception on attempt={} for {}: {}",
                            attempt, pjp.getSignature().toShortString(), t.getMessage());
                    continue;
                }
                throw t;
            }
        }

        // Should not reach here, but safety net
        if (lastException != null) throw lastException;
        throw new IllegalStateException("Resilient retry loop completed without result");
    }

    private Object executeWithCircuitBreaker(ProceedingJoinPoint pjp, Resilient resilient) throws Throwable {
        String cbName = resilient.circuitBreaker();
        if (cbName == null || cbName.isEmpty()) {
            return pjp.proceed();
        }

        CircuitBreaker cb = applicationContext.getBean(cbName, CircuitBreaker.class);
        long start = System.nanoTime();
        boolean success = false;

        try {
            cb.acquirePermission();
        } catch (CallNotPermittedException e) {
            log.warn("[Resilient] CircuitBreaker '{}' is OPEN — rejecting {}", cbName, pjp.getSignature().toShortString());
            throw new BizException(ErrorCode.AI_UNAVAILABLE);
        }

        Throwable thrown = null;
        try {
            Object result = pjp.proceed();
            success = true;
            return result;
        } catch (BizException e) {
            // Domain error — record as success, do not trip circuit
            success = true;
            throw e;
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            long durationNanos = System.nanoTime() - start;
            if (success) {
                cb.onSuccess(durationNanos, TimeUnit.NANOSECONDS);
            } else if (thrown != null) {
                cb.onError(durationNanos, TimeUnit.NANOSECONDS, thrown);
                log.warn("[Resilient] CircuitBreaker '{}' recorded failure: {}", cbName, thrown.getMessage());
            }
        }
    }

    private boolean isRetryable(Throwable t, Class<? extends Throwable>[] retryOn) {
        if (retryOn == null || retryOn.length == 0) return false;
        for (Class<? extends Throwable> retryable : retryOn) {
            if (retryable.isInstance(t)) return true;
        }
        return false;
    }
}
