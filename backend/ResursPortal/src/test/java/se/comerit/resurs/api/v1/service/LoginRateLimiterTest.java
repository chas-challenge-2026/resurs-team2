package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    @Test
    void blocksEmailAfterFiveRecordedFailures() {
        LoginRateLimiterService limiter = new LoginRateLimiterService(5, 20);
        String email = "user@example.com";
        String ip = "192.0.2.1";

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.isBlocked(email, ip)).isFalse();
            limiter.recordFailure(email, ip);
        }

        assertThat(limiter.isBlocked(email, ip)).isTrue();
    }

    @Test
    void blocksIpAfterTwentyRecordedFailures() {
        LoginRateLimiterService limiter = new LoginRateLimiterService(5,20);
        String ip = "192.0.2.1";

        for (int i = 0; i < 20; i++) {
            String email = "user" + i + "@example.com";
            assertThat(limiter.isBlocked(email, ip)).isFalse();
            limiter.recordFailure(email, ip);
        }

        assertThat(limiter.isBlocked("another@example.com", ip)).isTrue();
    }

    @Test
    void tracksEmailAndIpLimitsIndependently() {
        LoginRateLimiterService limiter = new LoginRateLimiterService(5,20);
        String email = "user@example.com";

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(email, "192.0.2.1");
        }

        assertThat(limiter.isBlocked(email, "192.0.2.2")).isTrue();
        assertThat(limiter.isBlocked("another@example.com", "192.0.2.1")).isFalse();
    }
}