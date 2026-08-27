package se.comerit.resurs.security;

import java.time.Instant;

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
