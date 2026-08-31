package se.comerit.resurs.api.v1.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    private final BankIdService bankIdService;
    private final CompanyRepository companyRepository;
    private final CaseWorkerRepository caseWorkerRepository;
    private final SessionTokenStore tokenStore;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(BankIdService bankIdService, CompanyRepository companyRepository,
            CaseWorkerRepository caseWorkerRepository, SessionTokenStore tokenStore,
            BCryptPasswordEncoder passwordEncoder) {
        this.bankIdService = bankIdService;
        this.companyRepository = companyRepository;
        this.caseWorkerRepository = caseWorkerRepository;
        this.tokenStore = tokenStore;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthTokens loginCompany(String orgNumber, String fingerprint) {
        if (!bankIdService.authenticate(orgNumber)) {
            throw InvalidCredentialsException.unauthorized("Invalid BankID authentication");
        }

        return companyRepository.findByOrgNumber(orgNumber)
                .map(company -> tokenStore.issue(new CompanyPrincipal(company.getId(), company.getName(), company.getOrgNumber()), fingerprint))
                .orElseThrow(() -> InvalidCredentialsException.unauthorized("Invalid login"));
    }

    public AuthTokens loginCaseWorker(String email, String password, String fingerprint) {
        return caseWorkerRepository.findByEmail(email)
                .flatMap(cw -> {
                    if (verifyCaseWorker(cw, password)) {
                        AuthTokens token = tokenStore.issue(
                                new CaseWorkerPrincipal(cw.getId(), cw.getName(), email), fingerprint);
                        return java.util.Optional.of(token);
                    }
                    return java.util.Optional.empty();
                })
                .orElseThrow(() -> InvalidCredentialsException.unauthorized("Invalid email or password"));
    }

    public AuthTokens refresh(String refreshToken, String fingerprint) {
        return tokenStore.rotate(refreshToken, fingerprint)
                .orElseThrow(() -> InvalidCredentialsException.unauthorized("Invalid or revoked token"));
    }

    public void logout(UserPrincipal principal) {
        tokenStore.revokeAllForUser(principal);
    }

    private boolean verifyCaseWorker(CaseWorker cw, String password) {
        return passwordEncoder.matches(password, cw.getPassword());
    }
}
