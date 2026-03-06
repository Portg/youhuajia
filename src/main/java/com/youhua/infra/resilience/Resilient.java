package com.youhua.infra.resilience;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Unified resilience annotation combining CircuitBreaker + Retry + Timeout.
 *
 * <p>Execution chain order: Timeout (outermost) → Retry → CircuitBreaker (innermost) → actual call.
 *
 * <ul>
 *   <li><b>CircuitBreaker</b> is innermost: when open, rejects immediately without wasting retries.
 *   <li><b>Retry</b> is middle: only retries specified exception types.
 *   <li><b>Timeout</b> is outermost: total time budget covering all retries.
 * </ul>
 *
 * <p>{@link com.youhua.common.exception.BizException} is always treated as a success
 * for circuit breaker purposes and is never retried.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Resilient {

    /**
     * Name of the CircuitBreaker bean to use. Empty string means no circuit breaker.
     */
    String circuitBreaker() default "";

    /**
     * Retry specification. Default: no retries.
     */
    RetrySpec retry() default @RetrySpec;

    /**
     * Timeout specification. Default: no timeout.
     */
    TimeoutSpec timeout() default @TimeoutSpec;
}
