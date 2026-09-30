package se.comerit.resurs.api.v1.service;


import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



    public class LoginRateLimiterService {

        private final int emailLimit;
        private final int ipLimit;

        private final Map<String, Bucket> emailBuckets = new ConcurrentHashMap<>();
        private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

        public LoginRateLimiterService(int emailLimit, int ipLimit) {
           this.emailLimit = emailLimit;
           this.ipLimit = ipLimit;
        }

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
                    key -> createBucket(emailLimit)
            );
        }

        private Bucket getIpBucket(String ip) {
            return ipBuckets.computeIfAbsent(
                    ip,
                    key -> createBucket(ipLimit)
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
