package com.youhua.testconfig;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Provides a mock StringRedisTemplate for @SpringBootTest tests
 * so they don't require a running Redis instance.
 */
@TestConfiguration
public class TestRedisConfig {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @SuppressWarnings("unchecked")
    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate mock = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(mock.opsForValue()).thenReturn(valueOps);
        when(mock.hasKey(anyString())).thenReturn(false);
        return mock;
    }
}
