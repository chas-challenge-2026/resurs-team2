package se.comerit.resurs.security;

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
        if (this instanceof CompanyPrincipal c)
            return c;
        throw new IllegalStateException("Not a company principal");
    }

    default CaseWorkerPrincipal asCaseWorker() {
        if (this instanceof CaseWorkerPrincipal cw)
            return cw;
        throw new IllegalStateException("Not a case worker principal");
    }
}