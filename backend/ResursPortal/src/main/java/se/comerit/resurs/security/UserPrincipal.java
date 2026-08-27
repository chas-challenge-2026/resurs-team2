package se.comerit.resurs.security;

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