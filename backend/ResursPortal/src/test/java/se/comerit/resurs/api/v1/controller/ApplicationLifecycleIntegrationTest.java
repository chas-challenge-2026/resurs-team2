package se.comerit.resurs.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.repository.ApplicationRepository;

/**
 * Full end-to-end application lifecycle through the real HTTP API:
 *
 * <ol>
 *   <li>company logs in, submits an application that lands in manual review, logs out</li>
 *   <li>case worker logs in, approves the application, logs out</li>
 *   <li>company logs in again and confirms the application is approved</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lifecycle;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource("/scoring-test.properties")
class ApplicationLifecycleIntegrationTest {

    private static final String UA = "integration-agent";

    private static final String COMPANY_ORG = "556000-1234";

    private static final String CASE_WORKER_EMAIL = "karin@resurs.se";
    private static final String CASE_WORKER_PASSWORD = "password123";

    /**
     * Financials crafted to produce exactly two scoring flags (liquidity below
     * the marginal band and a low cash-flow ratio) with no hard reject, so the
     * application lands in the manual-review bracket (UNDER_REVIEW).
     */
    private static final String MANUAL_REVIEW_REQUEST_JSON = """
            {
              "equity": 500000.0,
              "totalCapital": 1000000.0,
              "currentAssets": 150000.0,
              "currentLiabilities": 200000.0,
              "totalLiabilities": 500000.0,
              "operatingIncome": 150000.0,
              "netRevenue": 1000000.0,
              "requestedAmount": 100000,
              "purpose": "Rörelsekapital",
              "operatingCashFlow": 20000.0,
              "investingCashFlow": -10000.0,
              "interestExpenses": 20000.0,
              "industry": "IT"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Test
    @DisplayName("Full lifecycle: manual-review -> case worker approval -> company confirms")
    @Sql(statements = {
            "DELETE FROM documents",
            "DELETE FROM applications"
    })
    void fullApplicationLifecycle() throws Exception {
        // ------------------------------------------------------------------
        // 1. Company logs in and submits an application -> manual review.
        // ------------------------------------------------------------------
        String companyToken = loginCompany();

        MvcResult submit = mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(companyToken))
                        .header("User-Agent", UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MANUAL_REVIEW_REQUEST_JSON))
                .andExpect(status().isOk())
                .andReturn();
        long applicationId = Long.parseLong(submit.getResponse().getContentAsString());

        // The freshly submitted application must sit in the manual-review bracket.
        mockMvc.perform(get("/api/v1/applications/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(companyToken))
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("UNDER_REVIEW"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(companyToken))
                        .header("User-Agent", UA))
                .andExpect(status().isNoContent());

        // ------------------------------------------------------------------
        // 2. Case worker logs in, approves the application, logs out.
        // ------------------------------------------------------------------
        String caseWorkerToken = loginCaseWorker();

        mockMvc.perform(post("/api/v1/applications/{id}/decision", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(caseWorkerToken))
                        .header("User-Agent", UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED","comment":"Godkänd efter manuell granskning"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(applicationId))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.decisionReason").value("Godkänd efter manuell granskning"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caseWorkerToken))
                        .header("User-Agent", UA))
                .andExpect(status().isNoContent());

        // ------------------------------------------------------------------
        // 3. Company logs in again and confirms the application is approved.
        // ------------------------------------------------------------------
        String secondCompanyToken = loginCompany();

        mockMvc.perform(get("/api/v1/applications/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondCompanyToken))
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.id").value(applicationId))
                .andExpect(jsonPath("$.application.status").value("APPROVED"))
                .andExpect(jsonPath("$.application.decision").value("APPROVED"))
                .andExpect(jsonPath("$.application.decisionReason").value("Godkänd efter manuell granskning"));

        // Persisted state agrees with what the API reported.
        Application persisted = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(persisted.getStatus().name()).isEqualTo("APPROVED");
        assertThat(persisted.getDecision().name()).isEqualTo("APPROVED");
        assertThat(persisted.getDecisionReason()).isEqualTo("Godkänd efter manuell granskning");
    }

    private String loginCompany() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/company")
                        .header("User-Agent", UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNumber\":\"" + COMPANY_ORG + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COMPANY"))
                .andReturn();
        return readToken(result);
    }

    private String loginCaseWorker() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/caseWorker")
                        .header("User-Agent", UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + CASE_WORKER_EMAIL
                                + "\",\"password\":\"" + CASE_WORKER_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CASE_WORKER"))
                .andReturn();
        return readToken(result);
    }

    private String readToken(MvcResult result) throws java.io.IOException {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
