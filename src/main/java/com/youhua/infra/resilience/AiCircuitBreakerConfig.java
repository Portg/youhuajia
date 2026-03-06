package com.youhua.infra.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Programmatic Resilience4j CircuitBreaker configuration for the DeepSeek AI service.
 *
 * <p>Settings:
 * <ul>
 *   <li>failureRateThreshold: 50% — open circuit when half of calls fail</li>
 *   <li>minimumNumberOfCalls: 10 — need at least 10 calls before computing failure rate</li>
 *   <li>slidingWindowSize: 20 — count-based window of 20 calls</li>
 *   <li>waitDurationInOpenState: 30s — wait before moving to HALF_OPEN</li>
 * </ul>
 */
@Configuration
public class AiCircuitBreakerConfig {

    public static final String DEEPSEEK_CB_NAME = "deepseek";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(10)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    public static final String BAIDU_OCR_CB_NAME = "baiduOcr";

    @Bean(DEEPSEEK_CB_NAME)
    public CircuitBreaker deepseekCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(DEEPSEEK_CB_NAME);
    }

    @Bean(BAIDU_OCR_CB_NAME)
    public CircuitBreaker baiduOcrCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(BAIDU_OCR_CB_NAME);
    }
}
