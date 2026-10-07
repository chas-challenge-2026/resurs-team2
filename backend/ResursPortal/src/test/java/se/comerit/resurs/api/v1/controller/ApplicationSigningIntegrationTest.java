package se.comerit.resurs.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import org.springframework.test.web.servlet.MockMvc;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;
import se.comerit.resurs.security.WithCompany;

/**
 * End-to-end proof that a BankID signature gates submission: the signature
 * lands on the application row and in the audit log, and an application nobody
 * can sign is never written at all.
 *
 * <p>
 * The refusal case uses the mock's own rule rather than a stubbed-out bean, so
 * what is exercised is the wiring a production failure would travel through:
 * the service throws, and the submission has to come back rejected with
 * nothing persisted.
 *
 * <p>
 * Each test seeds its own company so the signatory under test is the only one
 * in the database, which is what makes the refusal case meaningful -- the
 * company exists and is registered, it simply has no one who may sign for it.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bankidsigning;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource("/scoring-test.properties")
class ApplicationSigningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String COMPANY_ORG = "556000-1234";
    private static final String SIGNATORY = "Test Person";

    private static final String VALID_REQUEST_JSON = """
            {
              "equity": 500000.0,
              "totalCapital": 1000000.0,
              "currentAssets": 400000.0,
              "currentLiabilities": 200000.0,
              "totalLiabilities": 500000.0,
              "operatingIncome": 150000.0,
              "netRevenue": 1000000.0,
              "requestedAmount": 300000,
              "purpose": "Rörelsekapital",
              "operatingCashFlow": 120000.0,
              "investingCashFlow": -50000.0,
              "interestExpenses": 20000.0,
              "industry": "IT"
            }
            """;

    /**
     * Replaces the fixture wholesale, so the only company in the database is
     * the one whose signatory this test is about. {@code signatorySql} is SQL,
     * hence NULL for a company with nobody recorded as allowed to sign.
     */
    private void seedCompanyWhoseSignatoryIs(String signatorySql) {
        jdbcTemplate.execute("DELETE FROM documents");
        jdbcTemplate.execute("DELETE FROM audit_log");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM companies");
        jdbcTemplate.execute(
                "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) "
                        + "VALUES (602, '556000-1234', "
                        + "X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', "
                        + "'Malmö Fastigheter AB', " + signatorySql + ")");
    }

    private List<String> auditEntries() {
        return auditLogRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getSequenceNumber(), b.getSequenceNumber()))
                .map(AuditLog::getEntry)
                .toList();
    }

    /** Drains the async scoring run so no later test inherits its writes. */
    private void awaitScoringComplete() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            List<String> entries = auditEntries();
            boolean scoringRun = entries.stream().anyMatch(e -> e.contains("SCORING_RUN"));
            boolean outcomeEtaSet = entries.stream()
                    .filter(e -> e.contains("\"action\":\"ETA_SET\""))
                    .count() >= 2;
            if (scoringRun && outcomeEtaSet) {
                return;
            }
            Thread.sleep(200);
        }
    }

    @Test
    @DisplayName("A signed submission stores the signature on the application and in the audit log")
    @WithCompany
    void signingIsRecordedOnApplicationAndAuditLog() throws Exception {
        seedCompanyWhoseSignatoryIs("'" + SIGNATORY + "'");

        mockMvc.perform(post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST_JSON))
                .andExpect(status().isOk());

        awaitScoringComplete();

        List<Application> applications = applicationRepository.findAll();
        assertThat(applications).hasSize(1);

        Application application = applications.get(0);
        assertThat(application.getBankidSignature()).startsWith("MOCK-SIG-");
        assertThat(application.getBankidSignedAt()).isNotNull();

        assertThat(auditEntries())
                .anySatisfy(entry -> assertThat(entry)
                        .contains("\"action\":\"APPLICATION_SIGNED\"")
                        .contains("\"orgNumber\":\"" + COMPANY_ORG + "\"")
                        .contains("\"signedBy\":\"" + SIGNATORY + "\""));
    }

    @Test
    @DisplayName("An application with no one who may sign it is rejected and never written")
    @WithCompany
    void applicationWithoutSignatoryIsRejected() throws Exception {
        seedCompanyWhoseSignatoryIs("NULL");

        mockMvc.perform(post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("BankID Signing Failed"));

        // The whole point: signing gates the submission, so a refused signature
        // leaves no application behind for anyone to pick up later.
        assertThat(applicationRepository.findAll()).isEmpty();

        List<String> entries = auditEntries();
        assertThat(entries)
                .noneMatch(e -> e.contains("\"action\":\"APPLICATION_SIGNED\""))
                .noneMatch(e -> e.contains("\"action\":\"APPLICATION_CREATED\""));
    }
}
