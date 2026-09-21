package se.comerit.resurs.api.v1.service;


import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import org.springframework.stereotype.Service;

import se.comerit.resurs.entity.CaseWorker;
import se.comerit.resurs.exception.InvalidCredentialsException;
import se.comerit.resurs.repository.CaseWorkerRepository;
import se.comerit.resurs.repository.CompanyRepository;
import se.comerit.resurs.security.AuthTokens;
import se.comerit.resurs.security.CaseWorkerPrincipal;
import se.comerit.resurs.security.CompanyPrincipal;
import se.comerit.resurs.security.SessionTokenStore;
import se.comerit.resurs.security.UserPrincipal;

@Service
public class AuthService {
    /**
     * Shared login failure so that the response is identical whether the org
     * number failed authentication (e.g. the BankID mock) or is not a
     * registered company. Revealing which one it was would allow anyone to
     * enumerate registered organisations.
     */
    private static final String LOGIN_FAILED = "Invalid login";

    private final BankIdService bankIdService;
    private final CompanyRepository companyRepository;
    private final CaseWorkerRepository caseWorkerRepository;
    private final SessionTokenStore tokenStore;
    private final Argon2PasswordEncoder argon2;

    public AuthService(BankIdService bankIdService, CompanyRepository companyRepository,
            CaseWorkerRepository caseWorkerRepository, SessionTokenStore tokenStore,Argon2PasswordEncoder argon2
            ) {
        this.bankIdService = bankIdService;
        this.companyRepository = companyRepository;
        this.caseWorkerRepository = caseWorkerRepository;
        this.tokenStore = tokenStore;
        this.argon2 = argon2;
    }

    public AuthTokens loginCompany(String orgNumber, String fingerprint) {
        if (!bankIdService.authenticate(orgNumber)) {
            throw InvalidCredentialsException.unauthorized(LOGIN_FAILED);
        }

        return companyRepository.findByOrgNumber(orgNumber)
                .map(company -> tokenStore.issue(new CompanyPrincipal(company.getId(), company.getName(), company.getOrgNumber()), fingerprint))
                .orElseThrow(() -> InvalidCredentialsException.unauthorized(LOGIN_FAILED));
    }

    public AuthTokens loginCaseWorker(String email, String password, String fingerprint) {
        return caseWorkerRepository.findByEmail(email)
                .flatMap(cw -> {
                    if (!argon2.matches(password, cw.getPassword())) {
                        return java.util.Optional.empty();
                    }
                        AuthTokens token = tokenStore.issue(
                                new CaseWorkerPrincipal(cw.getId(), cw.getName(), email), fingerprint);
                        return java.util.Optional.of(token);
                })
                .orElseThrow(() -> InvalidCredentialsException.unauthorized("Invalid email or password"));
    }

    public AuthTokens refresh(String refreshToken, String fingerprint) {
        return tokenStore.rotate(refreshToken, fingerprint)
                .orElseThrow(() -> InvalidCredentialsException.unauthorized("Invalid or revoked token"));
    }

    /**
     * Log out by revoking only the session that presented the access token.
     * Other active sessions for the same user (e.g. a second browser) stay
     * logged in — the caller is only terminating its own session.
     *
     * @param accessToken bearer token of the session being logged out
     */
    public void logout(String accessToken) {
        tokenStore.revoke(accessToken);
    }

    /**
     * Log the principal out of every active session (all devices/browsers).
     * Unlike {@link #logout(String)} this wipes all tokens of the user, for
     * example to clear stale logins after a suspected compromise.
     *
     * @param principal the user whose sessions are all revoked
     */
    public void logoutAll(UserPrincipal principal) {
        tokenStore.revokeAllForUser(principal);
    }

    private boolean verifyCaseWorker(CaseWorker cw, String password) {
        return argon2.matches(password, cw.getPassword());
    }
}
