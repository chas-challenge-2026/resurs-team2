package se.comerit.resurs.config;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import se.comerit.resurs.api.v1.service.ResursCryptoService;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.entity.CaseWorker;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;
import se.comerit.resurs.repository.CaseWorkerRepository;
import se.comerit.resurs.repository.CompanyRepository;

/**
 * Ensures the plaintext seed rows inserted by {@code data.sql} are stored
 * encrypted at rest in non-test profiles. Companies, the demo application and
 * the demo audit log are matched by their raw, unencrypted values and rewritten
 * in place with the active {@link PiiCodec} encryption plus the derived blind
 * index. Rows that are already encrypted (or created through the application)
 * are left untouched, and any seed row missing from the database is inserted.
 *
 * <p>The raw lookups and rewrites go through {@link JdbcTemplate} directly so
 * the plaintext columns are never read back through
 * {@link PiiAttributeConverter}, which would fail to base64-decode them.</p>
 *
 * <p>Tests keep using {@code data.sql} directly together with
 * {@link PlainPiiCodec}, so this runner is excluded under the {@code "test"}
 * profile.</p>
 * 
 * TODO Remove this test initializer
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

    private static final String DEMO_REQUESTED_AMOUNT = "500000.00";
    private static final String DEMO_APPLICATION_PURPOSE = "Expansion av verksamheten";
    private static final String DEMO_SCORING_RESULT = "FLAGGED: soliditet=0.28 (OK), "
            + "likviditetsgrad=0.95 (FLAGGED), skuldsättningsgrad=2.1 (OK)";

    private final PiiCodec codec;
    private final ResursCryptoService crypto;
    private final JdbcTemplate jdbcTemplate;
    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final AuditLogRepository auditLogRepository;
    private final CaseWorkerRepository caseWorkerRepository;
    private final Argon2PasswordEncoder argon2;

    public PiiInitializer(PiiCodec codec, ResursCryptoService crypto, JdbcTemplate jdbcTemplate,
            CompanyRepository companyRepository,
            ApplicationRepository applicationRepository,
            AuditLogRepository auditLogRepository,
            CaseWorkerRepository caseWorkerRepository,
            Argon2PasswordEncoder argon2) {
        this.codec = codec;
        this.crypto = crypto;
        this.jdbcTemplate = jdbcTemplate;
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.auditLogRepository = auditLogRepository;
        this.caseWorkerRepository = caseWorkerRepository;
        this.argon2 = argon2;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedCompanies();
        seedDemoApplication();

        if (caseWorkerRepository.findByEmail(SEED_CASE_WORKER_EMAIL).isEmpty()) {
            caseWorkerRepository.save(new CaseWorker(
                    "Karin Handläggare",
                    SEED_CASE_WORKER_EMAIL,
                    argon2.encode(SEED_CASE_WORKER_PASSWORD)));
            log.info("Seeded case worker {}", SEED_CASE_WORKER_EMAIL);
        }
    }

    private void seedCompanies() {
        for (SeedCompany seed : SEED) {
            byte[] index = crypto.blindIndex(seed.orgNumber());
            if (companyRepository.findByOrgNumberIndex(index).isPresent()) {
                continue;
            }
            Optional<Long> rawId = queryLong(
                    "SELECT id FROM companies WHERE org_number = ? ORDER BY id LIMIT 1",
                    seed.orgNumber());
            if (rawId.isPresent()) {
                jdbcTemplate.update(
                        "UPDATE companies SET org_number = ?, company_name = ?, "
                                + "authorized_signatory = ?, org_number_index = ? WHERE id = ?",
                        codec.encode(seed.orgNumber()),
                        codec.encode(seed.name()),
                        codec.encode(seed.authorizedSignatory()),
                        index,
                        rawId.get());
                log.info("Encrypted seed company {}", seed.orgNumber());
            } else {
                companyRepository.save(new Company(
                        seed.orgNumber(),
                        seed.name(),
                        seed.authorizedSignatory()));
                log.info("Seeded company {}", seed.orgNumber());
            }
        }
    }

    private void seedDemoApplication() {
        Company company = companyRepository
                .findByOrgNumberIndex(crypto.blindIndex(SEED[0].orgNumber()))
                .orElseThrow(() -> new IllegalStateException("Seed company missing after seeding"));

        Optional<Long> rawId = queryLong(
                "SELECT id FROM applications WHERE company_id = ? AND purpose = ? ORDER BY id LIMIT 1",
                company.getId(), DEMO_APPLICATION_PURPOSE);
        if (rawId.isPresent()) {
            jdbcTemplate.update(
                    "UPDATE applications SET requested_amount = ?, purpose = ?, "
                            + "decision_reason = ?, scoring_result = ? WHERE id = ?",
                    codec.encode(DEMO_REQUESTED_AMOUNT),
                    codec.encode(DEMO_APPLICATION_PURPOSE),
                    null,
                    codec.encode(DEMO_SCORING_RESULT),
                    rawId.get());
            log.info("Encrypted seed demo application for {}", SEED[0].orgNumber());
        } else if (applicationRepository.findByCompanyId(company.getId()).isEmpty()) {
            applicationRepository.save(new Application(
                    company,
                    new BigDecimal(DEMO_REQUESTED_AMOUNT),
                    DEMO_APPLICATION_PURPOSE,
                    ApplicationStatus.UNDER_REVIEW,
                    null,
                    null,
                    DEMO_SCORING_RESULT));
            log.info("Seeded demo application for {}", SEED[0].orgNumber());
        }

        applicationRepository.findByCompanyId(company.getId()).stream().findFirst()
                .ifPresent(this::seedAuditLog);
    }

    private void seedAuditLog(Application application) {
        String created = "{\"action\":\"APPLICATION_CREATED\",\"orgNumber\":\"" + SEED[0].orgNumber() + "\"}";
        String scoring = "{\"action\":\"SCORING_RUN\",\"result\":\"REVIEW\",\"flags\":\"1\"}";

        for (String entry : new String[] { created, scoring }) {
            Optional<UUID> id = queryUuid(
                    "SELECT id FROM audit_log WHERE application_id = ? AND entry = ? "
                            + "ORDER BY sequence_number LIMIT 1",
                    application.getId(), entry);
            id.ifPresent(uuid -> {
                jdbcTemplate.update("UPDATE audit_log SET entry = ? WHERE id = ?",
                        codec.encode(entry), uuid);
                log.info("Encrypted seed audit log for {}", SEED[0].orgNumber());
            });
        }

        if (auditLogRepository.findByApplication(application, Sort.unsorted()).isEmpty()) {
            long seq = auditLogRepository.getNextSequenceNumber(application);
            auditLogRepository.save(new AuditLog(application, seq + 1, "", null, created));
            auditLogRepository.save(new AuditLog(application, seq + 2, "", null, scoring));
            log.info("Seeded audit log for {}", SEED[0].orgNumber());
        }
    }

    private Optional<Long> queryLong(String sql, Object... args) {
        return Optional.ofNullable(jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : null, args));
    }

    private Optional<UUID> queryUuid(String sql, Object... args) {
        return Optional.ofNullable(jdbcTemplate.query(sql, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, args));
    }
}