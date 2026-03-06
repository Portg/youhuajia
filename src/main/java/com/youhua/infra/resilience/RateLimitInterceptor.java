package com.youhua.infra.resilience;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * HandlerInterceptor that enforces Redis-based sliding-window rate limits.
 *
 * <p>Reads {@link RateLimit} from the handler method. If the annotation is present,
 * the interceptor increments a Redis counter for the current user/IP and rejects
 * requests that exceed the configured permit threshold.
 *
 * <p>Redis key format: {@code ratelimit:{prefix}:{userId or IP}}
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "ratelimit";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String dimension = resolveIdentifier(request);
        String redisKey = String.join(":", KEY_PREFIX, rateLimit.key(), dimension);

        long count = incrementAndGetCount(redisKey, rateLimit.window());

        if (count > rateLimit.permits()) {
            log.warn("Rate limit exceeded: key={}, count={}, permits={}", redisKey, count, rateLimit.permits());
            throw new BizException(ErrorCode.RATE_LIMITED);
        }

        return true;
    }

    /**
     * Atomically increments the counter. Sets TTL only on first access to avoid
     * resetting the window on every request.
     */
    private long incrementAndGetCount(String redisKey, int windowSeconds) {
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            log.error("Redis INCR returned null for key={}", redisKey);
            // Fail open — do not block the request when Redis is unavailable
            return 0;
        }
        if (count == 1) {
            // First request in this window: set the expiry
            redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        }
        return count;
    }

    /**
     * Returns userId (set by {@code JwtAuthFilter}) or falls back to the client IP.
     */
    private String resolveIdentifier(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId != null) {
            return "user:" + userId;
        }
        return "ip:" + getClientIp(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            // Take the first (original client) address
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
