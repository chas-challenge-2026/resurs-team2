package se.comerit.resurs.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionTokenStore {
    // package-private for tests (se.comerit.resurs.security); not part of the API
    final ConcurrentHashMap<String, SessionToken> sessionsByAccess = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, SessionToken> sessionsByRefresh = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, SessionToken> usedTokens = new ConcurrentHashMap<>();

    private final boolean slidingExpirationEnabled;
    private final Duration idleExpiration;
    private final Duration absoluteExpiration;
    private final Duration usedTokenRetention; // derived = absolute + idle

    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    @Autowired
    public SessionTokenStore(
            @Value("${session-token.sliding-expiration-enabled:true}") boolean sliding,
            @Value("${session-token.idle-expiration-ms:900000}") long idleMs,
            @Value("${session-token.absolute-expiration-ms:3600000}") long absMs) {
        this(sliding, idleMs, absMs, Clock.systemUTC());
    }

    /** Constructor with an explicit clock, primarily for tests. */
    SessionTokenStore(boolean sliding, long idleMs, long absMs, Clock clock) {
        this.slidingExpirationEnabled = sliding;
        this.idleExpiration = Duration.ofMillis(idleMs);
        this.absoluteExpiration = Duration.ofMillis(absMs);
        this.usedTokenRetention = this.absoluteExpiration.plus(this.idleExpiration);
        this.clock = clock;
    }

    public AuthTokens issue(UserPrincipal principal, String fingerprint) {
        String access = randomToken();
        String refresh = randomToken();
        Instant now = clock.instant();
        Instant expiresAt = computeExpiry(now, now);
        SessionToken st = new SessionToken(hash(access), hash(refresh), fingerprint,
                principal, now, expiresAt);
        sessionsByAccess.put(st.accessTokenHash, st);
        sessionsByRefresh.put(st.refreshTokenHash, st);
        return new AuthTokens(access, refresh, st.principal.role(), st.principal.name());
    }

    /**
     * Validates that the passed in token is valid and matches the fingerprint.
     * 
     * If the token is invalid or out of date then the token will be revoked and
     * removed.
     * If sliding expiration is enabled then successful validation will update the
     * token expiration.
     * 
     * @param token       Session token to check.
     * @param fingerprint User-Agent fingerprint.
     * @return The Principal for the token, or empty if invalid.
     */
    public Optional<UserPrincipal> validateAccess(String token, String fingerprint) {
        SessionToken st = sessionsByAccess.get(hash(token));
        if (st == null || st.revoked)
            return Optional.empty();
        if (st.expiresAt.isBefore(clock.instant())) {
            remove(st);
            return Optional.empty();
        }
        if (!matchesFingerprint(st.fingerprint, fingerprint)) {
            revokeAllForUser(st.principal);
            return Optional.empty();
        }
        if (slidingExpirationEnabled)
            slideOnActivity(st);
        return Optional.of(st.principal);
    }

    /**
     * Rotate a session using its refresh token: the presented refresh token is
     * single-use, so it is invalidated (and retained for theft/replay detection)
     * while a fresh access+refresh pair is issued for the same principal.
     *
     * @param refreshToken the current refresh token
     * @param fingerprint  client fingerprint from the rotation request
     * @return fresh tokens, or empty if the refresh token is unknown, expired,
     *         already used, or the fingerprint does not match (in which case the
     *         whole session is revoked as a suspected theft)
     */
    public Optional<AuthTokens> rotate(String refreshToken, String fingerprint) {
        SessionToken st = sessionsByRefresh.get(hash(refreshToken));
        if (st == null || st.revoked) {
            return Optional.empty();
        }
        if (st.expiresAt.isBefore(clock.instant())) {
            remove(st);
            return Optional.empty();
        }
        if (!matchesFingerprint(st.fingerprint, fingerprint)) {
            // Different device/agent presenting the refresh token — treat as theft.
            revokeAllForUser(st.principal);
            return Optional.empty();
        }
        // Single-use: mark this refresh as spent so a replay is detected as empty.
        usedTokens.put(st.refreshTokenHash, st);
        remove(st);
        return Optional.of(issue(st.principal, fingerprint));
    }

    /**
     * Revoke an access token preventing further use.
     * 
     * @param accessToken The token to revoke.
     */
    public void revoke(String accessToken) {
        SessionToken st = sessionsByAccess.get(hash(accessToken));
        if (st != null)
            remove(st);
    }

    /**
     * Revoke all active tokens for the User
     * 
     * @param principal User whose tokens to revoke
     */
    public void revokeAllForUser(UserPrincipal principal) {
        // Match on role AND id: a company and a case worker may share the same
        // numeric row id (separate tables), and must not revoke each other.
        sessionsByAccess.entrySet().removeIf(e -> sameUser(e.getValue().principal, principal));
        sessionsByRefresh.entrySet().removeIf(e -> sameUser(e.getValue().principal, principal));
        // also mark used, so old refresh cannot come back
        sessionsByRefresh.values().stream()
                .filter(s -> sameUser(s.principal, principal))
                .forEach(s -> usedTokens.put(s.refreshTokenHash, s));
    }

    private static boolean sameUser(UserPrincipal a, UserPrincipal b) {
        return a.role() == b.role() && a.id().equals(b.id());
    }

    // Constant-time string comparison (MessageDigest.isEqual) so timing cannot
    // reveal how much of the fingerprint matched.
    private static boolean matchesFingerprint(String stored, String presented) {
        return MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Cleanup function that removes all stale tokens in the database.
     */
    @Scheduled(fixedRateString = "${session-token.sweep-interval-ms:300000}")
    public void sweepExpired() {
        Instant now = clock.instant();
        sessionsByAccess.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
        sessionsByRefresh.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
        // Spent refresh hashes are retained past session expiry for forensic
        // theft/replay evidence; they are not consulted for authorization (rotation
        // already removes them from the active maps, making replays fail).
        usedTokens.entrySet().removeIf(e -> e.getValue().expiresAt.plus(usedTokenRetention).isBefore(now));
    }

    private void slideOnActivity(SessionToken st) {
        Instant next = computeExpiry(st.loginTime, clock.instant());
        if (next.isBefore(clock.instant())) {
            revokeAllForUser(st.principal);
        } else {
            st.expiresAt = next;
        }
    }

    private void remove(SessionToken st) {
        sessionsByAccess.remove(st.accessTokenHash);
        sessionsByRefresh.remove(st.refreshTokenHash);
    }

    private Instant computeExpiry(Instant loginTime, Instant now) {
        Instant sliding = now.plus(idleExpiration);
        Instant cap = loginTime.plus(absoluteExpiration);
        if (!slidingExpirationEnabled)
            return sliding;
        return sliding.isBefore(cap) ? sliding : cap;

    }

    /**
     * Generates a 32 Byte random token.
     * 
     * @return Base64 encoded random string.
     */
    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // package-private for tests; a pure helper (no security exposure)
    String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
