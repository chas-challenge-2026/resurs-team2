package se.comerit.resurs.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import se.comerit.resurs.entity.CaseWorker;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.repository.CaseWorkerRepository;
import se.comerit.resurs.repository.CompanyRepository;

@Service
public class AuthService {
    private final CompanyRepository companyRepository;
    private final CaseWorkerRepository caseWorkerRepository;

    public AuthService(CompanyRepository companyRepository, CaseWorkerRepository caseWorkerRepository) {
        this.companyRepository = companyRepository;
        this.caseWorkerRepository = caseWorkerRepository;
    }

    public Optional<Company> findCompany(@Nonnull String orgNumber) {
        return companyRepository.findByOrgNumber(orgNumber);
    }

    public Optional<CaseWorker> loginCaseWorker(@Nonnull String email, @Nonnull String passworld) {
        return caseWorkerRepository.findByEmailAndPassword(email, md5Hash(passworld));
    }

    // MD5 — weak, but matches DB seed
    // TODO: migrate to bcrypt before go-live
    private String md5Hash(String input) {
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
