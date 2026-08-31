package se.comerit.resurs.security;

/**
 * A single-issue response of tokens returned to a client after an
 * authentication/rotation: an opaque access token for API calls plus a
 * single-use refresh token for obtaining the next pair. Also carries the
 * authenticated principal's role and display name so the caller need not parse
 * the tokens.
 */
public record AuthTokens(String accessToken, String refreshToken, PrincipalRole role, String name) {

}
