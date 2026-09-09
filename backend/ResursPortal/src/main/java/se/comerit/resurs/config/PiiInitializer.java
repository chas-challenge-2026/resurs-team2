package se.comerit.resurs.config;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.CaseWorker;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.CaseWorkerRepository;
import se.comerit.resurs.repository.CompanyRepository;

/**
 * TODO: This is just a test initalizer until actual account creation is up and running
 * 
 * Seeds the mock companies (matching the BankID whitelist) and a demo
 * application as encrypted rows. Runs in production/local profiles only
 * ({@code "!test"}) so tests keep seeding via {@code data.sql}.
 */
@Component
@Profile("!test")
public class PiiInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PiiInitializer.class);

    record SeedCompany(String orgNumber, String name, String authorizedSignatory) {
    }

    private static final SeedCompany[] SEED = {
            new SeedCompany("556000-1234", "Malmö Fastigheter AB", "Anders Karlsson"),
            new SeedCompany("556000-5678", "Göteborg Handel AB", "Maria Svensson")
    };

    private static final String SEED_CASE_WORKER_EMAIL = "karin@resurs.se";
    private static final String SEED_CASE_WORKER_PASSWORD = "password123";

    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final CaseWorkerRepository caseWorkerRepository;
    private final Argon2PasswordEncoder argon2;

    public PiiInitializer(CompanyRepository companyRepository,
            ApplicationRepository applicationRepository,
            CaseWorkerRepository caseWorkerRepository,
            Argon2PasswordEncoder argon2) {
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.caseWorkerRepository = caseWorkerRepository;
        this.argon2 = argon2;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (SeedCompany seed : SEED) {
            if (companyRepository.findByOrgNumber(seed.orgNumber()).isPresent()) {
                continue;
            }
            companyRepository.save(encryptCompany(seed));
            log.info("Seeded company {}", seed.orgNumber());
        }

        SeedCompany first = SEED[0];
        companyRepository.findByOrgNumber(first.orgNumber())
                .filter(company -> applicationRepository.findByCompanyId(company.getId()).isEmpty())
                .ifPresent(company -> {
                    applicationRepository.save(encryptDemoApplication(company));
                    log.info("Seeded demo application for {}", first.orgNumber());
                });

        if (caseWorkerRepository.findByEmail(SEED_CASE_WORKER_EMAIL).isEmpty()) {
            caseWorkerRepository.save(new CaseWorker(
                    "Karin Handläggare",
                    SEED_CASE_WORKER_EMAIL,
                    argon2.encode(SEED_CASE_WORKER_PASSWORD)));
            log.info("Seeded case worker {}", SEED_CASE_WORKER_EMAIL);
        }
    }

    private Company encryptCompany(SeedCompany seed) {
        return new Company(
                seed.orgNumber(),
                seed.name(),
                seed.authorizedSignatory());
    }

    private Application encryptDemoApplication(Company company) {
        String purpose = "Expansion av verksamheten";

        return new Application(
                company,
                new BigDecimal("500000.00"),
                purpose,
                ApplicationStatus.UNDER_REVIEW,
                null,
                null,
                null);
    }
}