package se.comerit.resurs.security;

public final record CaseWorkerPrincipal(Long id, String name, String email) implements UserPrincipal {
    @Override
    public PrincipalRole role() {
        return PrincipalRole.CASE_WORKER;
    }
}