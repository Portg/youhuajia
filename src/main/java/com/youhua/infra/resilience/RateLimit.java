package com.youhua.infra.resilience;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method for Redis-based rate limiting.
 *
 * <p>The rate limit key is: {@code ratelimit:{key}:{userId or IP}}.
 * If the user is authenticated, userId from request attribute is used; otherwise the client IP.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Limiting dimension prefix, e.g. "ocr-upload", "suggestion". */
    String key();

    /** Maximum number of requests allowed within the time window. */
    int permits() default 10;

    /** Time window in seconds. */
    int window() default 60;
}
