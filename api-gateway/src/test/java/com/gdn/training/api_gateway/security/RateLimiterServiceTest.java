package com.gdn.training.api_gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.gdn.training.api_gateway.config.RateLimiterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.RedisSystemException;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    private static final String CLIENT_KEY = "client-1";
    private static final String REDIS_KEY = "ratelimit:" + CLIENT_KEY;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setRequestsPerMinute(2);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        rateLimiterService = new RateLimiterService(properties, redisTemplate);
    }

    @Test
    void consumesTokensWhileWithinLimit() {
        when(valueOperations.increment(REDIS_KEY)).thenReturn(1L, 2L);
        when(redisTemplate.getExpire(eq(REDIS_KEY), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(50_000L, 40_000L);

        RateLimitResult first = rateLimiterService.consume(CLIENT_KEY);
        RateLimitResult second = rateLimiterService.consume(CLIENT_KEY);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remainingTokens()).isEqualTo(1);
        assertThat(first.nanosToReset()).isGreaterThan(0);

        assertThat(second.allowed()).isTrue();
        assertThat(second.remainingTokens()).isZero();
        assertThat(second.nanosToReset()).isGreaterThan(0);

        verify(redisTemplate, times(1)).expire(eq(REDIS_KEY), eq(Duration.ofMinutes(1)));
    }

    @Test
    void rejectsWhenLimitExceeded() {
        when(valueOperations.increment(REDIS_KEY)).thenReturn(1L, 2L, 3L);
        when(redisTemplate.getExpire(eq(REDIS_KEY), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(50_000L);

        rateLimiterService.consume(CLIENT_KEY);
        rateLimiterService.consume(CLIENT_KEY);
        RateLimitResult blocked = rateLimiterService.consume(CLIENT_KEY);

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.remainingTokens()).isZero();
        assertThat(blocked.nanosToReset()).isGreaterThan(0);

        verify(redisTemplate, times(3))
                .getExpire(eq(REDIS_KEY), eq(TimeUnit.MILLISECONDS));
        verify(redisTemplate, times(1))
                .expire(eq(REDIS_KEY), eq(Duration.ofMinutes(1)));
    }

    @Test
    void fallsBackToFullWindowWhenTtlNotAvailable() {
        when(valueOperations.increment(REDIS_KEY)).thenReturn(1L);
        when(redisTemplate.getExpire(eq(REDIS_KEY), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(null);

        RateLimitResult result = rateLimiterService.consume(CLIENT_KEY);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingTokens()).isEqualTo(1);
        assertThat(result.nanosToReset())
                .isEqualTo(Duration.ofMinutes(1).toNanos());

        verify(redisTemplate, times(1)).expire(eq(REDIS_KEY), eq(Duration.ofMinutes(1)));
    }

    @Test
    void failsOpenWhenRedisThrowsException() {
        when(redisTemplate.opsForValue())
                .thenThrow(new RedisSystemException("Redis down", new RuntimeException()));

        RateLimitResult result = rateLimiterService.consume(CLIENT_KEY);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingTokens()).isEqualTo(2);
    }
}
