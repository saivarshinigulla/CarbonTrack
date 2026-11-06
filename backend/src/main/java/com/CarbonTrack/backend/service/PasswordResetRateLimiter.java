package com.CarbonTrack.backend.service;

import com.CarbonTrack.backend.util.Sha256;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class PasswordResetRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final long EMAIL_LIMIT = 3;
    private static final long IP_LIMIT = 10;

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>("""
                    local ip_count = redis.call('INCR', KEYS[2])
                    if ip_count == 1 then
                      redis.call('EXPIRE', KEYS[2], ARGV[1])
                    end
                    if ip_count > tonumber(ARGV[3]) then
                      return 0
                    end
                    local email_count = redis.call('INCR', KEYS[1])
                    if email_count == 1 then
                      redis.call('EXPIRE', KEYS[1], ARGV[1])
                    end
                    if email_count > tonumber(ARGV[2]) then
                      return 0
                    end
                    return 1
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public PasswordResetRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean allowRequest(String normalizedEmail, String clientIp) {
        Long allowed = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(
                        "carbontrack:password-reset:email:" + Sha256.hex(normalizedEmail),
                        "carbontrack:password-reset:ip:" + Sha256.hex(clientIp)
                ),
                Long.toString(WINDOW.toSeconds()),
                Long.toString(EMAIL_LIMIT),
                Long.toString(IP_LIMIT)
        );
        if (allowed == null) {
            throw new IllegalStateException(
                    "Password-reset rate limiter returned no result");
        }
        return allowed == 1;
    }
}
