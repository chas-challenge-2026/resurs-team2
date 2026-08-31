package se.comerit.resurs.service;

import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import se.comerit.resurs.entity.CaseWorker;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.repository.CaseWorkerRepository;
import se.comerit.resurs.repository.CompanyRepository;

@Service("legacyAuthService")
public class AuthService {
    private final CompanyRepository companyRepository;
    private final CaseWorkerRepository caseWorkerRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(CompanyRepository companyRepository, CaseWorkerRepository caseWorkerRepository,
            BCryptPasswordEncoder passwordEncoder) {
        this.companyRepository = companyRepository;
        this.caseWorkerRepository = caseWorkerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<Company> findCompany(@Nonnull String orgNumber) {
        return companyRepository.findByOrgNumber(orgNumber);
    }

    public Optional<CaseWorker> loginCaseWorker(@Nonnull String email, @Nonnull String password) {
        return caseWorkerRepository.findByEmail(email)
                .filter(cw -> passwordEncoder.matches(password, cw.getPassword()));
    }
}
