package se.comerit.resurs.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SessionTokenStoreTest {

    private static final String FP = "Mozilla/5.0|127.0.0.1";
    private static final String OTHER_FP = "Other-Agent|10.0.0.1";

    private final CompanyPrincipal company =
            new CompanyPrincipal(1L, "Malmö Fastigheter AB", "556000-1234");
    private final CaseWorkerPrincipal worker =
            new CaseWorkerPrincipal(2L, "Karin Handläggare", "karin@resurs.se");

    private final MutableClock clock = new MutableClock();

    private SessionTokenStore store(boolean sliding, long idleMs, long absMs) {
        return new SessionTokenStore(sliding, idleMs, absMs, clock);
    }

    // ---------- issue ----------

    @Test
    void issueReturnsOpaqueDistinctTokensWithPrincipalData() {
        SessionTokenStore store = store(true, 50, 500);

        AuthTokens tokens = store.issue(company, FP);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.accessToken()).isNotEqualTo(tokens.refreshToken());
        assertThat(tokens.role()).isEqualTo(PrincipalRole.COMPANY);
        assertThat(tokens.name()).isEqualTo(company.name());
    }

    @Test
    void issueProducesDistinctTokenPairs() {
        SessionTokenStore store = store(true, 50, 500);

        AuthTokens a = store.issue(company, FP);
        AuthTokens b = store.issue(company, FP);

        assertThat(a.accessToken()).isNotEqualTo(b.accessToken());
        assertThat(a.refreshToken()).isNotEqualTo(b.refreshToken());
    }

    @Test
    void onlyHashedTokensAreStored() {
        SessionTokenStore store = store(true, 50, 500);

        AuthTokens tokens = store.issue(company, FP);

        // The raw opaque tokens are never the stored keys — only their SHA-256 hashes.
        assertThat(store.sessionsByAccess).doesNotContainKey(tokens.accessToken());
        assertThat(store.sessionsByRefresh).doesNotContainKey(tokens.refreshToken());
        assertThat(store.sessionsByAccess).containsKey(store.hash(tokens.accessToken()));
        assertThat(store.sessionsByRefresh).containsKey(store.hash(tokens.refreshToken()));
    }

    // ---------- validateAccess ----------

    @Test
    void validateAccessReturnsPrincipalForMatchingFingerprint() {
        SessionTokenStore store = store(true, 200, 1000);

        AuthTokens tokens = store.issue(company, FP);

        assertThat(store.validateAccess(tokens.accessToken(), FP)).isPresent().get()
                .isSameAs(company);
    }

    @Test
    void validateAccessRejectsUnknownToken() {
        SessionTokenStore store = store(true, 200, 1000);

        assertThat(store.validateAccess("never-issued", FP)).isEmpty();
    }

    @Test
    void validateAccessRejectsExpiredToken() {
        SessionTokenStore store = store(true, 40, 500);

        AuthTokens tokens = store.issue(company, FP);
        clock.advance(Duration.ofMillis(70)); // safely past 40ms idle

        assertThat(store.validateAccess(tokens.accessToken(), FP)).isEmpty();
    }

    @Test
    void validateAccessIsRejectedAfterIdleWindowElapses() {
        SessionTokenStore store = store(true, 60, 60_000);

        AuthTokens tokens = store.issue(company, FP);
        assertThat(store.validateAccess(tokens.accessToken(), FP)).isPresent();

        clock.advance(Duration.ofMillis(100)); // safely past 60ms
        assertThat(store.validateAccess(tokens.accessToken(), FP)).isEmpty();
    }

    @Test
    void slidingOnAccessExtendsWindow() {
        // idle 400ms, absolute large so only idle matters.
        SessionTokenStore store = store(true, 400, 60_000);

        AuthTokens tokens = store.issue(company, FP);

        // Each validation within the current window slides it forward by idle.
        clock.advance(Duration.ofMillis(100)); // t=100 within 400 -> slide
        assertThat(store.validateAccess(tokens.accessToken(), FP)).isPresent();

        clock.advance(Duration.ofMillis(100)); // t=200 within slid window -> slide
        assertThat(store.validateAccess(tokens.accessToken(), FP)).isPresent();

        clock.advance(Duration.ofMillis(100)); // t=300 within slid window -> slide
        assertThat(store.validateAccess(tokens.accessToken(), FP)).isPresent();

        // Wait past the (re-slid) window: it must now be rejected.
        clock.advance(Duration.ofMillis(500)); // t=800 > slid expiry (~700)
        assertThat(store.validateAccess(tokens.accessToken(), FP)).isEmpty();
    }

    @Test
    void absoluteCapBypassesSliding() {
        // Idle is long but absolute cap is short: cap must win.
        SessionTokenStore store = store(true, 10_000, 200);

        AuthTokens tokens = store.issue(company, FP);
        clock.advance(Duration.ofMillis(300)); // past 200ms absolute, well under 10s idle

        assertThat(store.validateAccess(tokens.accessToken(), FP)).isEmpty();
    }

    @Test
    void wrongFingerprintRejectsAndRevokesSession() {
        SessionTokenStore store = store(true, 500, 60_000);

        AuthTokens tokens = store.issue(company, FP);

        assertThat(store.validateAccess(tokens.accessToken(), OTHER_FP)).isEmpty();
        // Session was nuked: even the correct fingerprint can no longer authenticate.
        assertThat(store.validateAccess(tokens.accessToken(), FP)).isEmpty();
    }

    @Test
    void expiredTokenIsRemovedOnLookup() {
        SessionTokenStore store = store(true, 40, 500);

        AuthTokens tokens = store.issue(company, FP);
        assertThat(store.sessionsByAccess).hasSize(1);

        clock.advance(Duration.ofMillis(70));
        store.validateAccess(tokens.accessToken(), FP);

        assertThat(store.sessionsByAccess).isEmpty();
        assertThat(store.sessionsByRefresh).isEmpty();
    }

    // ---------- revoke ----------

    @Test
    void revokeLogsOutSingleToken() {
        SessionTokenStore store = store(true, 500, 60_000);

        AuthTokens a = store.issue(company, FP);
        AuthTokens b = store.issue(company, FP);

        store.revoke(a.accessToken());

        assertThat(store.validateAccess(a.accessToken(), FP)).isEmpty();
        assertThat(store.validateAccess(b.accessToken(), FP)).isPresent();
    }

    @Test
    void revokeUnknownTokenIsNoOp() {
        SessionTokenStore store = store(true, 500, 60_000);

        store.revoke("not-a-real-token");

        assertThat(store.sessionsByAccess).isEmpty();
    }

    @Test
    void revokeAllForUserRevokesEverySession() {
        SessionTokenStore store = store(true, 500, 60_000);

        AuthTokens a = store.issue(company, FP);
        AuthTokens b = store.issue(company, FP);

        store.revokeAllForUser(company);

        assertThat(store.validateAccess(a.accessToken(), FP)).isEmpty();
        assertThat(store.validateAccess(b.accessToken(), FP)).isEmpty();
    }

    @Test
    void revokeAllForUserOnlyAffectsTargetUser() {
        SessionTokenStore store = store(true, 500, 60_000);

        AuthTokens companyTokens = store.issue(company, FP);
        AuthTokens workerTokens = store.issue(worker, FP);

        store.revokeAllForUser(company);

        assertThat(store.validateAccess(companyTokens.accessToken(), FP)).isEmpty();
        assertThat(store.validateAccess(workerTokens.accessToken(), FP)).isPresent();
    }

    @Test
    void revokeAllForUserDoesNotCrossRevokeSameIdDifferentRole() {
        // Regression: a company and a case worker can share the same numeric row id
        // (separate tables). Revoking one must NEVER kill the other's session.
        SessionTokenStore store = store(true, 500, 60_000);

        CompanyPrincipal companyId1 = new CompanyPrincipal(1L, "Acme AB", "556000-9999");
        CaseWorkerPrincipal workerId1 = new CaseWorkerPrincipal(1L, "OWL Worker", "owl@resurs.se");

        AuthTokens companyTokens = store.issue(companyId1, FP);
        AuthTokens workerTokens = store.issue(workerId1, FP);

        store.revokeAllForUser(companyId1);

        assertThat(store.validateAccess(companyTokens.accessToken(), FP)).isEmpty();
        assertThat(store.validateAccess(workerTokens.accessToken(), FP)).isPresent();
    }

    @Test
    void fingerprintMismatchDoesNotRevokeOtherRoleWithSameIdOrSimilarFingerprint() {
        // The theft tripwire for one user must not take down a same-id different-role
        // session, and only an exact fingerprint mismatch should revoke.
        SessionTokenStore store = store(true, 500, 60_000);

        CompanyPrincipal companyId1 = new CompanyPrincipal(1L, "Acme AB", "556000-9999");
        CaseWorkerPrincipal workerId1 = new CaseWorkerPrincipal(1L, "OWL Worker", "owl@resurs.se");

        AuthTokens companyTokens = store.issue(companyId1, FP);
        AuthTokens workerTokens = store.issue(workerId1, FP);

        // Present the company access token with a *slightly* different fingerprint.
        assertThat(store.validateAccess(companyTokens.accessToken(), FP + ".")).isEmpty();
        // The worker's session (same id, different role) is untouched.
        assertThat(store.validateAccess(workerTokens.accessToken(), FP)).isPresent();
    }

    @Test
    void constantTimeFingerprintMatchesOnlyOnExactBytes() {
        SessionTokenStore store = store(true, 10_000, 60_000);

        AuthTokens tokens = store.issue(company, FP);

        // Exact fingerprint -> authenticated.
        assertThat(store.validateAccess(tokens.accessToken(), FP)).isPresent();
        // A near-identical fingerprint (single trailing char) is NOT a match.
        assertThat(store.validateAccess(tokens.accessToken(), FP + ".")).isEmpty();
    }

    // ---------- rotate ----------

    @Test
    void rotateIssuesFreshPairAndRetiresOldPair() {
        SessionTokenStore store = store(true, 10_000, 60_000);

        AuthTokens original = store.issue(company, FP);
        AuthTokens rotated = store.rotate(original.refreshToken(), FP).orElseThrow();

        // New tokens differ from the old pair.
        assertThat(rotated.accessToken()).isNotEqualTo(original.accessToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThat(rotated.role()).isEqualTo(PrincipalRole.COMPANY);
        assertThat(rotated.name()).isEqualTo(company.name());

        // Old access token is dead after rotation.
        assertThat(store.validateAccess(original.accessToken(), FP)).isEmpty();

        // New access token works.
        assertThat(store.validateAccess(rotated.accessToken(), FP)).isPresent();
    }

    @Test
    void rotateIsSingleUse() {
        SessionTokenStore store = store(true, 10_000, 60_000);

        AuthTokens original = store.issue(company, FP);
        assertThat(store.rotate(original.refreshToken(), FP)).isPresent();
        // Second use of the same refresh token is rejected (replay/theft detection).
        assertThat(store.rotate(original.refreshToken(), FP)).isEmpty();
    }

    @Test
    void rotateUnknownOrBlankRefreshIsRejected() {
        SessionTokenStore store = store(true, 10_000, 60_000);

        assertThat(store.rotate("never-issued", FP)).isEmpty();
    }

    @Test
    void rotateAfterAbsoluteCapIsRejected() {
        SessionTokenStore store = store(true, 10_000, 200);

        AuthTokens original = store.issue(company, FP);
        clock.advance(Duration.ofMillis(300)); // past 200ms absolute cap

        assertThat(store.rotate(original.refreshToken(), FP)).isEmpty();
    }

    @Test
    void rotateWithWrongFingerprintRevokesWholeSession() {
        SessionTokenStore store = store(true, 10_000, 60_000);

        AuthTokens original = store.issue(company, FP);

        // Refresh attempted from a different device/agent -> theft -> reject.
        assertThat(store.rotate(original.refreshToken(), OTHER_FP)).isEmpty();

        // The entire session is gone: the originally issued access token is dead too,
        // and the refresh can no longer be used from the correct fingerprint.
        assertThat(store.validateAccess(original.accessToken(), FP)).isEmpty();
        assertThat(store.rotate(original.refreshToken(), FP)).isEmpty();
    }

    @Test
    void rotateRestoresSlidingSessionForSamePrincipal() {
        SessionTokenStore store = store(true, 10_000, 60_000);

        AuthTokens a = store.issue(company, FP);
        AuthTokens rotated = store.rotate(a.refreshToken(), FP).orElseThrow();

        // A second rotation from the (fresh) refresh token keeps the session alive.
        AuthTokens again = store.rotate(rotated.refreshToken(), FP).orElseThrow();
        assertThat(store.validateAccess(again.accessToken(), FP)).isPresent();
    }

    // ---------- sweep ----------

    @Test
    void sweepRemovesExpiredSessions() {
        SessionTokenStore store = store(true, 40, 500);

        AuthTokens tokens = store.issue(company, FP);
        assertThat(store.sessionsByAccess).hasSize(1);

        clock.advance(Duration.ofMillis(70));
        store.sweepExpired();

        assertThat(store.sessionsByAccess).isEmpty();
        assertThat(store.sessionsByRefresh).isEmpty();
    }

    @Test
    void sweepKeepsUnExpiredSessions() {
        SessionTokenStore store = store(true, 5_000, 60_000);

        AuthTokens tokens = store.issue(company, FP);

        store.sweepExpired();

        assertThat(store.sessionsByAccess).hasSize(1);
        assertThat(store.validateAccess(tokens.accessToken(), FP)).isPresent();
    }

    // ---------- sliding disabled ----------

    @Test
    void slidingDisabledKeepsFixedWindow() {
        SessionTokenStore store = store(false, 120, 60_000);

        AuthTokens tokens = store.issue(company, FP);

        // Sliding disabled: validation halfway through the idle window does NOT re-arm it.
        clock.advance(Duration.ofMillis(60));
        assertThat(store.validateAccess(tokens.accessToken(), FP)).isPresent();

        // But the fixed 120ms window has now elapsed (60+80=140 > 120).
        clock.advance(Duration.ofMillis(80));
        assertThat(store.validateAccess(tokens.accessToken(), FP)).isEmpty();
    }

    // ---------- principal helpers ----------

    @Test
    void principalAccessorHelpers() {
        assertThat(company.asCompany()).isSameAs(company);
        assertThat(worker.asCaseWorker()).isSameAs(worker);
        assertThatThrownBy(company::asCaseWorker)
                .isInstanceOf(IllegalStateException.class);
        assertThat(company.id()).isEqualTo(1L);
        assertThat(worker.id()).isEqualTo(2L);
    }

    @Test
    void helperHashIsStableAndSha256() {
        SessionTokenStore store = store(true, 50, 500);
        String a = store.hash("the-token");
        String b = store.hash("the-token");
        assertThat(a).isEqualTo(b);
        assertThat(store.hash("other")).isNotEqualTo(a);
    }
}
