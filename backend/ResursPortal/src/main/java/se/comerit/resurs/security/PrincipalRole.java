package se.comerit.resurs.security;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The two authentication roles a {@link UserPrincipal} can hold. Maps to Spring
 * Security's {@code ROLE_} prefix authorities for the API authorization rules
 * (e.g. {@code CASE_WORKER} gates {@code /api/v1/backoffice/**}).
 */
@Schema(description = "Authentication role of a principal")
public enum PrincipalRole {
    @Schema(description = "Company principal")
    COMPANY,
    @Schema(description = "Case worker principal")
    CASE_WORKER
}