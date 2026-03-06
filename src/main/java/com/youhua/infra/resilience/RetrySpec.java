package com.youhua.infra.resilience;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Retry configuration for {@link Resilient}.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface RetrySpec {

    /**
     * Maximum number of retry attempts (excluding the initial call).
     * E.g. maxAttempts=1 means 2 total calls.
     */
    int maxAttempts() default 0;

    /**
     * Exception types that trigger a retry. Only these exceptions (and their subclasses)
     * will be retried. All other exceptions propagate immediately.
     */
    Class<? extends Throwable>[] retryOn() default {};
}
