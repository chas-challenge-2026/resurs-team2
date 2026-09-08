package se.comerit.resurs.config;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.CompanyRepository;

/**
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

    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;

    public PiiInitializer(CompanyRepository companyRepository,
            ApplicationRepository applicationRepository) {
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
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
                null,
                "[{\"action\":\"APPLICATION_CREATED\"}]");
    }
}