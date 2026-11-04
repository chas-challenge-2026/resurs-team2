package se.comerit.resurs.security;

public final record CompanyPrincipal(Long id, String name, String orgNumber) implements UserPrincipal {
    @Override
    public PrincipalRole role() {
        return PrincipalRole.COMPANY;
    }
}