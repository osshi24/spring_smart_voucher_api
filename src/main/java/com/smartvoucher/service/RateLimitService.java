package com.smartvoucher.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, Long> redisTemplate;

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

    public RateLimitResult checkAndIncrementMinute(Long apiKeyId, int limitPerMinute) {
        long minuteWindow = Instant.now().getEpochSecond() / 60;
        String key = "rate_limit:min:" + apiKeyId + ":" + minuteWindow;
        long resetEpochSeconds = (minuteWindow + 1) * 60;

        Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key), "120");
        long currentCount = count != null ? count : 1L;

        return new RateLimitResult(currentCount, limitPerMinute, resetEpochSeconds);
    }

    public RateLimitResult checkAndIncrement(Long apiKeyId, int limitPerMinute) {
        return checkAndIncrementMinute(apiKeyId, limitPerMinute);
    }

    public RateLimitResult checkAndIncrementDay(Long apiKeyId, int limitPerDay) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String dayKey = String.format("%d%02d%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String key = "rate_limit:day:" + apiKeyId + ":" + dayKey;

        ZonedDateTime endOfDay = now.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC);
        long ttlSeconds = endOfDay.toEpochSecond() - now.toEpochSecond();
        long resetEpochSeconds = endOfDay.toEpochSecond();

        Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key), String.valueOf(ttlSeconds));
        long currentCount = count != null ? count : 1L;

        return new RateLimitResult(currentCount, limitPerDay, resetEpochSeconds);
    }

    public long getDailyUsage(Long apiKeyId) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String dayKey = String.format("%d%02d%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String key = "rate_limit:day:" + apiKeyId + ":" + dayKey;

        Object val = redisTemplate.opsForValue().get(key);
        if (val == null) return 0L;
        return val instanceof Long l ? l : Long.parseLong(val.toString());
    }

    public long getMinuteUsage(Long apiKeyId) {
        long minuteWindow = Instant.now().getEpochSecond() / 60;
        String key = "rate_limit:min:" + apiKeyId + ":" + minuteWindow;

        Object val = redisTemplate.opsForValue().get(key);
        if (val == null) return 0L;
        return val instanceof Long l ? l : Long.parseLong(val.toString());
    }
}
