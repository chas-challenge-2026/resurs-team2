package se.comerit.resurs.security;

/**
 * The two authentication roles a {@link UserPrincipal} can hold. Maps to Spring
 * Security's {@code ROLE_} prefix authorities for the API authorization rules
 * (e.g. {@code CASE_WORKER} gates {@code /api/v1/backoffice/**}).
 */
public enum PrincipalRole {
    COMPANY, CASE_WORKER
}
