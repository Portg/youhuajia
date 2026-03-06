package com.youhua.infra.resilience;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Timeout configuration for {@link Resilient}.
 * When seconds &gt; 0, a total time budget is enforced across all retries.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeoutSpec {

    /**
     * Total timeout in seconds. 0 means no timeout.
     */
    int seconds() default 0;
}
