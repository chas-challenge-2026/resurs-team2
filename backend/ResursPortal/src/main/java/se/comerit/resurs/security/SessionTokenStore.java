package se.comerit.resurs.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    public SessionTokenStore(
            @Value("${session-token.sliding-expiration-enabled:true}") boolean sliding,
            @Value("${session-token.idle-expiration-ms:900000}") long idleMs,
            @Value("${session-token.absolute-expiration-ms:3600000}") long absMs) {
        this.slidingExpirationEnabled = sliding;
        this.idleExpiration = Duration.ofMillis(idleMs);
        this.absoluteExpiration = Duration.ofMillis(absMs);
        this.usedTokenRetention = this.absoluteExpiration.plus(this.idleExpiration);
    }

    public AuthTokens issue(UserPrincipal principal, String fingerprint) {
        String access = randomToken();
        String refresh = randomToken();
        Instant now = Instant.now();
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
        if (st.expiresAt.isBefore(Instant.now())) {
            remove(st);
            return Optional.empty();
        }
        if (!st.fingerprint.equals(fingerprint)) {
            revokeAllForUser(st.principal);
            return Optional.empty();
        }
        if (slidingExpirationEnabled)
            slideOnActivity(st);
        return Optional.of(st.principal);
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
        Long id = principal.id();
        sessionsByAccess.entrySet().removeIf(e -> e.getValue().principal.id().equals(id));
        sessionsByRefresh.entrySet().removeIf(e -> e.getValue().principal.id().equals(id));
        // also mark used, so old refresh cannot come back
        sessionsByRefresh.values().stream()
                .filter(s -> s.principal.id().equals(id))
                .forEach(s -> usedTokens.put(s.refreshTokenHash, s));
    }

    /**
     * Cleanup function that removes all stale tokens in the database.
     */
    @Scheduled(fixedRateString = "${session-token.sweep-interval-ms:300000}")
    public void sweepExpired() {
        Instant now = Instant.now();
        sessionsByAccess.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
        sessionsByRefresh.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
        // Used tokens are kept longer to check for stolen tokens or replay attacks
        usedTokens.entrySet().removeIf(e -> e.getValue().expiresAt.plus(usedTokenRetention).isBefore(now));
    }

    private void slideOnActivity(SessionToken st) {
        Instant next = computeExpiry(st.loginTime, Instant.now());
        if (next.isBefore(Instant.now())) {
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
                    .encodeToString(md.digest(token.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
