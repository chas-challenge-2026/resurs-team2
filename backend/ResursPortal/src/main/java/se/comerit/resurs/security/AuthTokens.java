package se.comerit.resurs.security;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single-issue response of tokens produced by an authentication/rotation:
 * an opaque access token for API calls plus a single-use refresh token for
 * obtaining the next pair. Also carries the authenticated principal's role
 * and display name so the caller need not parse the tokens.
 * <p>
 * These values are never serialized into an HTTP response body — the
 * {@link se.comerit.resurs.security.SessionCookie} layer writes them to the
 * {@code httpOnly} session cookies instead.
 */
@Schema(description = "Token pair produced after authentication or token refresh (carried in httpOnly cookies)")
public record AuthTokens(
        @Schema(description = "Opaque access token, carried exclusively in the httpOnly resurs_access session cookie",
                example = "5f4dcc3b...")
        String accessToken,
        @Schema(description = "Single-use refresh token, carried exclusively in the httpOnly resurs_refresh session cookie")
        String refreshToken,
        @Schema(description = "Role of the authenticated principal")
        PrincipalRole role,
        @Schema(description = "Display name of the authenticated principal", example = "Acme AB")
        String name) {

}