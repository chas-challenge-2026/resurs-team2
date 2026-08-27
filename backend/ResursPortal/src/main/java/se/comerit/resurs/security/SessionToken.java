package se.comerit.resurs.security;

import java.time.Instant;

/**
 * Internal in-memory session record. Only SHA-256 hashes of the random tokens
 * are stored (never the raw tokens), along with the client fingerprint the
 * session is bound to, the owning principal, and lifetime metadata. Managed
 * exclusively by {@link SessionTokenStore}.
 */
public class SessionToken {
    final String accessTokenHash;
    final String refreshTokenHash;
    /** user-agent fingerprinting */
    final String fingerprint;
    final UserPrincipal principal;
    final Instant loginTime;
    Instant expiresAt;
    boolean revoked;

    public SessionToken(String accessTokenHash, String refreshTokenHash, String fingerprint,
            UserPrincipal principal, Instant loginTime, Instant expiresAt) {
        this.accessTokenHash = accessTokenHash;
        this.refreshTokenHash = refreshTokenHash;
        this.fingerprint = fingerprint;
        this.principal = principal;
        this.loginTime = loginTime;
        this.expiresAt = expiresAt;
    }
}
