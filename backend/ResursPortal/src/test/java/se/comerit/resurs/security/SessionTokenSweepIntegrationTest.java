package se.comerit.resurs.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards the scheduled cleanup that depends on {@code @EnableScheduling} being
 * active. If scheduling is removed (regression), the in-memory store grows
 * without bound and this test fails.
 *
 * A @Primary store with a short idle + absolute cap is used, and the sweep
 * interval is overridden so the test is fast and deterministic.
 */
@SpringBootTest(properties = {
        "session-token.sweep-interval-ms=200",
        "spring.sql.init.mode=never"
})
@ActiveProfiles("test")
class SessionTokenSweepIntegrationTest {

    private static final String FP = "sweep-agent|127.0.0.1";

    @TestConfiguration
    static class SweepConfig {
        @Bean
        @Primary
        SessionTokenStore shortLivedStore() {
            // idle 150ms, absolute 400ms; sweep interval resolves from the
            // @Scheduled placeholder to the overridden 200ms.
            return new SessionTokenStore(true, 150, 400);
        }
    }

    @Autowired
    private SessionTokenStore store;

    @Test
    void scheduledSweepRemovesExpiredSessions() throws Exception {
        AuthTokens tokens = store.issue(
                new CompanyPrincipal(99L, "Sweep AB", "999999-9999"), FP);

        String accessHash = store.hash(tokens.accessToken());
        String refreshHash = store.hash(tokens.refreshToken());
        assertThat(store.sessionsByAccess).containsKey(accessHash);
        assertThat(store.sessionsByRefresh).containsKey(refreshHash);

        // Wait well past the absolute cap (400ms) plus a couple of sweep intervals.
        Thread.sleep(1500);

        assertThat(store.sessionsByAccess).doesNotContainKey(accessHash);
        assertThat(store.sessionsByRefresh).doesNotContainKey(refreshHash);
    }
}
