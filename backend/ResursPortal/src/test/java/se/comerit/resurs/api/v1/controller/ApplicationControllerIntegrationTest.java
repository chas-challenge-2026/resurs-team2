package se.comerit.resurs.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.security.WithCaseWorker;
import se.comerit.resurs.security.WithCompany;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    private static final String COMPANY_ORG = "556000-1234";

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

    @Nested
    @DisplayName("GET apply")
    class ShowApplyForm {

        @Test
        void unauthenticatedIs401() throws Exception {
            mockMvc.perform(get("/api/v1/application/apply"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @WithCaseWorker
        void wrongRoleCaseWorkerIs403() throws Exception {
            mockMvc.perform(get("/api/v1/application/apply"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Access Denied"));
        }

        @Test
        @WithCompany
        void returnsCurrentCompanyDetails() throws Exception {
            mockMvc.perform(get("/api/v1/application/apply"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.companyName").value("Malmö Fastigheter AB"))
                    .andExpect(jsonPath("$.orgNumber").value(COMPANY_ORG));
        }

        @Test
        @WithCompany(name = "Alternativ Bolag AB", orgNumber = "556000-7777")
        void reflectsPrincipalFields() throws Exception {
            mockMvc.perform(get("/api/v1/application/apply"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.companyName").value("Alternativ Bolag AB"))
                    .andExpect(jsonPath("$.orgNumber").value("556000-7777"));
        }
    }

    @Nested
    @DisplayName("POST apply")
    class Submit {

        @Test
        void unauthenticatedIs401() throws Exception {
            mockMvc.perform(post("/api/v1/application/apply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @WithCaseWorker
        void wrongRoleCaseWorkerIs403() throws Exception {
            mockMvc.perform(post("/api/v1/application/apply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Access Denied"));
        }

        @Test
        @WithCompany
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (600, '556000-1234', 'Malmö Fastigheter AB', 'Test Person')"
        })
        void submitsAndPersistsApplication() throws Exception {
            mockMvc.perform(post("/api/v1/application/apply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST_JSON))
                    .andExpect(status().isOk());

            assertThat(applicationRepository.findAll()).hasSize(1);
            Application app = applicationRepository.findAll().get(0);
            assertThat(app.getCompany().getOrgNumber()).isEqualTo(COMPANY_ORG);
            assertThat(app.getPurpose()).isEqualTo("Rörelsekapital");
            assertThat(app.getRequestedAmount()).isEqualByComparingTo("300000");

            // Audit log must contain both expected entries, created before scoring.
            String log = app.getAuditLog();
            assertThat(log)
                    .contains("\"action\":\"APPLICATION_CREATED\"")
                    .contains("\"orgNumber\":\"556000-1234\"")
                    .contains("\"action\":\"SCORING_RUN\"");
            int created = log.indexOf("APPLICATION_CREATED");
            int scoring = log.indexOf("SCORING_RUN");
            assertThat(scoring).isGreaterThan(created);

            // A decision/reason should be produced by scoring.
            assertThat(app.getDecisionReason()).isNotBlank();
        }

        @Test
        @WithCompany(orgNumber = "556000-9999")
        void missingCompanyIs400() throws Exception {
            mockMvc.perform(post("/api/v1/application/apply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET view application")
    class ViewApplication {

        @Test
        void unauthenticatedIs401() throws Exception {
            mockMvc.perform(get("/api/v1/application/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @WithCompany
        void nonExistentApplicationIs404() throws Exception {
            mockMvc.perform(get("/api/v1/application/99999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Application Not Found"));
        }

        @Test
        @WithCompany(orgNumber = "556000-1234")
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (700, '556000-1234', 'Malmö Fastigheter AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) VALUES (700, 700, 300000.00, 'Rörelsekapital', 'UNDER_REVIEW', NULL, NULL, NULL, '[]')",
                "INSERT INTO documents (id, application_id, filename, doc_type) VALUES (700, 700, 'bokaplan.pdf', 'BOKFORING')"
        })
        void companyCanViewOwnApplication() throws Exception {
            mockMvc.perform(get("/api/v1/application/700"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.application.id").value(700))
                    .andExpect(jsonPath("$.application.companyName").value("Malmö Fastigheter AB"))
                    .andExpect(jsonPath("$.application.orgNumber").value("556000-1234"))
                    .andExpect(jsonPath("$.application.purpose").value("Rörelsekapital"))
                    .andExpect(jsonPath("$.application.status").value("UNDER_REVIEW"))
                    .andExpect(jsonPath("$.documents.length()").value(1))
                    .andExpect(jsonPath("$.documents[0].filename").value("bokaplan.pdf"))
                    .andExpect(jsonPath("$.documents[0].docType").value("BOKFORING"));
        }

        @Test
        @WithCompany(orgNumber = "556000-9999")
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (701, '556000-1234', 'Ägarens Bolag AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) VALUES (701, 701, 300000.00, 'Rörelsekapital', 'UNDER_REVIEW', NULL, NULL, NULL, '[]')"
        })
        void companyCannotViewAnotherCompanysApplication() throws Exception {
            // The authenticated company (556000-9999) must NOT be able to see
            // an application owned by a different company (556000-1234). It
            // should be indistinguishable from a missing application.
            mockMvc.perform(get("/api/v1/application/701"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Application Not Found"));
        }

        @Test
        @WithCaseWorker(name = "Karin Handläggare")
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (702, '556000-1234', 'Malmö Fastigheter AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) VALUES (702, 702, 400000.00, 'Expansion', 'APPROVED', 'APPROVED', 'Godkänd', NULL, '[]')"
        })
        void caseWorkerCanViewAnyApplication() throws Exception {
            mockMvc.perform(get("/api/v1/application/702"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.application.id").value(702))
                    .andExpect(jsonPath("$.application.companyName").value("Malmö Fastigheter AB"))
                    .andExpect(jsonPath("$.application.status").value("APPROVED"))
                    .andExpect(jsonPath("$.application.decision").value("APPROVED"))
                    .andExpect(jsonPath("$.application.decisionReason").value("Godkänd"))
                    .andExpect(jsonPath("$.workerName").value("Karin Handläggare"))
                    .andExpect(jsonPath("$.documents").isEmpty());
        }

        @Test
        @WithCaseWorker
        void caseWorkerNonExistentApplicationIs404() throws Exception {
            mockMvc.perform(get("/api/v1/application/99999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Application Not Found"));
        }
    }
}
