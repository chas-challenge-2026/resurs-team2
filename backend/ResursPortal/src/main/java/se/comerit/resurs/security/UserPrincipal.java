package se.comerit.resurs.security;

import se.comerit.resurs.exception.ForbiddenPrincipalException;

/**
 * Sealed union of the two roles that can authenticate against the API: a
 * {@link CompanyPrincipal} (applies for funding) and a
 * {@link CaseWorkerPrincipal} (reviews/manages applications). Enables
 * compile-time exhaustive handling and role-based authorization via
 * {@link #role()}.
 */
public sealed interface UserPrincipal permits CompanyPrincipal, CaseWorkerPrincipal {
    Long id();

    String name();

    PrincipalRole role();

    default CompanyPrincipal asCompany() {
        if (this instanceof CompanyPrincipal c) {
            return c;
        }
        throw new ForbiddenPrincipalException(
                "Expected principal of type CompanyPrincipal but was "
                        + actualType(this) + " (id=" + id() + ")");
    }

    default CaseWorkerPrincipal asCaseWorker() {
        if (this instanceof CaseWorkerPrincipal cw) {
            return cw;
        }
        throw new ForbiddenPrincipalException(
                "Expected principal of type CaseWorkerPrincipal but was "
                        + actualType(this) + " (id=" + id() + ")");
    }

    private static String actualType(UserPrincipal principal) {
        return principal == null ? "null" : principal.getClass().getSimpleName();
    }
}