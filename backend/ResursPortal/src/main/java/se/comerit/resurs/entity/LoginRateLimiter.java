package se.comerit.resurs.entity;

import org.bouncycastle.util.IPAddress;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;

import java.awt.*;
import java.security.Key;
import java.time.Duration;

import static java.awt.SystemColor.WINDOW;
import static org.hibernate.engine.internal.Versioning.increment;

public class LoginRateLimiter {

    private static final int maxEmailAttempts = 5;
    private static final int maxIpAttempts = 20;
    private static final Duration Window = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public LoginRateLimiter (StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isBlocked(String email, String ip) {
        return attempts("email:" + email) >= maxEmailAttempts
                || attempts("ip:" + ip) >= maxIpAttempts;
    }

    public void failed(String email, String ip) {
        increase("email:" + email);
        increase("ip:" + ip);
    }

    public void success(String email) {
        redisTemplate.delete(key("email:" + email));
    }

    private int attempts(String identifier) {
        String value = redisTemplate.opsForValue().get(key(identifier));
        return value == null ? 0 : Integer.parseInt(value);
    }

    private void increase(String identifier) {
        String key = key(identifier);

        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, Window);
        }
    }

    private String key(String identifier) {
        return "login:case-worker:" + identifier;
    }

}
