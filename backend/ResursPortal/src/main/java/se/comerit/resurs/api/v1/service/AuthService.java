package se.comerit.resurs.api.v1.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import se.comerit.resurs.entity.CaseWorker;
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

    public Optional<AuthTokens> loginCompany(String orgNumber, String fingerprint) {
        if (!bankIdService.authenticate(orgNumber)) {
            return Optional.empty();
        }

        return companyRepository.findByOrgNumber(orgNumber).map(company -> tokenStore
                .issue(new CompanyPrincipal(company.getId(), company.getName(), company.getOrgNumber()), fingerprint));
    }

    public Optional<AuthTokens> loginCaseWorker(String email, String password, String fingerprint) {
        return caseWorkerRepository.findByEmail(email)
                .flatMap(cw -> {
                    if (verifyCaseWorker(cw, password)) {
                        AuthTokens token = tokenStore.issue(
                                new CaseWorkerPrincipal(cw.getId(), cw.getName(), email), fingerprint);
                        return Optional.of(token);
                    }
                    return Optional.empty();
                });
    }

    public Optional<AuthTokens> refresh(String refreshToken, String fingerprint) {
        return tokenStore.rotate(refreshToken, fingerprint);
    }

    public void logout(UserPrincipal principal) {
        tokenStore.revokeAllForUser(principal);
    }

    private boolean verifyCaseWorker(CaseWorker cw, String password) {
        String stored = cw.getPassword();
        if (isBcrypt(stored)) {
            return passwordEncoder.matches(password, stored);
        }
        // TODO Remove legacy
        // Legacy MD5 row — constant-time compare, then upgrade.
        boolean ok = MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                md5(password).getBytes(StandardCharsets.UTF_8));
        if (ok) {
            cw.setPassword(passwordEncoder.encode(password));
            caseWorkerRepository.save(cw);
        }
        return ok;
    }

    private boolean isBcrypt(String stored) {
        return stored.startsWith("$2a$") || stored.startsWith("$2b$");
    }

    // TODO: migrate to bcrypt before go-live
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
