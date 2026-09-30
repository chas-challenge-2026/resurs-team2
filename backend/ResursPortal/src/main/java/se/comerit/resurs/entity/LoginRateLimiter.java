package se.comerit.resurs.entity;


import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


    @Service
    public class LoginRateLimiter {

        private static final int EMAIL_LIMIT = 5;
        private static final int IP_LIMIT = 20;

        private final Map<String, Bucket> emailBuckets = new ConcurrentHashMap<>();
        private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

        public boolean isBlocked(String email, String ip) {
            return getEmailBucket(email).getAvailableTokens() == 0
                    || getIpBucket(ip).getAvailableTokens() == 0;
        }

        public void recordFailure(String email, String ip) {
            getEmailBucket(email).tryConsume(1);
            getIpBucket(ip).tryConsume(1);
        }

        private Bucket getEmailBucket(String email) {
            return emailBuckets.computeIfAbsent(
                    email,
                    key -> createBucket(EMAIL_LIMIT)
            );
        }

        private Bucket getIpBucket(String ip) {
            return ipBuckets.computeIfAbsent(
                    ip,
                    key -> createBucket(IP_LIMIT)
            );
        }

        private Bucket createBucket(int limit) {
            return Bucket.builder()
                    .addLimit(rule -> rule
                            .capacity(limit)
                            .refillGreedy(limit, Duration.ofMinutes(15)))
                    .build();
        }
    }
