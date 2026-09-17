package se.comerit.resurs.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import se.comerit.resurs.security.AuthTokens;
import se.comerit.resurs.security.PrincipalRole;
import se.comerit.resurs.security.UserPrincipal;

/**
 * Principal-only response for the auth endpoints (login, refresh, {@code /me}).
 * Token values are never returned in a body — they travel exclusively in the
 * {@code httpOnly} session cookies. The role is served in the canonical API
 * spelling ({@code CASEWORKER}) so clients no longer need to map the backend
 * enum name {@code CASE_WORKER}.
 */
@Schema(description = "Authenticated principal returned by the auth endpoints")
public record PrincipalResponse(
        @Schema(description = "Canonical role of the principal: COMPANY or CASEWORKER", example = "COMPANY")
        String role,
        @Schema(description = "Display name of the principal", example = "Acme AB")
        String name) {

    public static PrincipalResponse from(AuthTokens tokens) {
        return new PrincipalResponse(canonicalRole(tokens.role()), tokens.name());
    }

    public static PrincipalResponse from(UserPrincipal principal) {
        return new PrincipalResponse(canonicalRole(principal.role()), principal.name());
    }

    /**
     * The API speaks {@code CASEWORKER}; the backend authority/model spells the
     * enum {@code CASE_WORKER}. Canonicalizing here keeps both sides in sync.
     */
    public static String canonicalRole(PrincipalRole role) {
        return role == PrincipalRole.CASE_WORKER ? "CASEWORKER" : role.name();
    }
}