package se.comerit.resurs.security;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single-issue response of tokens returned to a client after an
 * authentication/rotation: an opaque access token for API calls plus a
 * single-use refresh token for obtaining the next pair. Also carries the
 * authenticated principal's role and display name so the caller need not parse
 * the tokens.
 */
@Schema(description = "Token pair returned after authentication or token refresh")
public record AuthTokens(
        @Schema(description = "Opaque access token; submit as 'Authorization: Bearer <token>'",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "Single-use refresh token for rotating to a new token pair")
        String refreshToken,
        @Schema(description = "Role of the authenticated principal")
        PrincipalRole role,
        @Schema(description = "Display name of the authenticated principal", example = "Acme AB")
        String name) {

}