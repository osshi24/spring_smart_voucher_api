package com.smartvoucher.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, Long> redisTemplate;

    // Lua script: atomically increment counter, set TTL of 120s on first call
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            """
            local key = KEYS[1]
            local ttl = tonumber(ARGV[1])
            if redis.call('exists', key) == 0 then
                redis.call('set', key, 0, 'EX', ttl)
            end
            return redis.call('incr', key)
            """,
            Long.class
    );

    public record RateLimitResult(long count, int limit, long resetEpochSeconds) {
        public boolean isExceeded() {
            return count > limit;
        }
        public long remaining() {
            return Math.max(0, limit - count);
        }
    }

    public RateLimitResult checkAndIncrement(Long apiKeyId, int limitPerMinute) {
        long minuteWindow = Instant.now().getEpochSecond() / 60;
        String key = "rate_limit:" + apiKeyId + ":" + minuteWindow;
        long resetEpochSeconds = (minuteWindow + 1) * 60;

        Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key), "120");
        long currentCount = count != null ? count : 1L;

        return new RateLimitResult(currentCount, limitPerMinute, resetEpochSeconds);
    }
}
