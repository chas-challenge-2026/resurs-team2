package se.comerit.resurs.exception;

/**
 * Thrown when a principal is accessed through a role-specific accessor it does
 * not belong to, e.g. calling {@code asCompany()} on a case-worker principal.
 * Maps to HTTP 403 Forbidden.
 */
public class ForbiddenPrincipalException extends IllegalStateException {

    public ForbiddenPrincipalException(String message) {
        super(message);
    }
}
