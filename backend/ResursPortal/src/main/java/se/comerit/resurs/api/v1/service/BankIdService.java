package se.comerit.resurs.api.v1.service;

/**
 * Authentication half of the BankID integration: proving which company is
 * logging in.
 *
 * <p>
 * Like {@link BankIdSigningService} this is a seam a real integration replaces
 * without callers changing. Both halves are selected together by
 * {@code resurs.bankid.mode}, so authentication and signing cannot end up on
 * different implementations.
 */
public interface BankIdService {
    /**
     * @return true if the org was successfully authenticated via BankID
     */
    boolean authenticate(String orgNumber);
}
