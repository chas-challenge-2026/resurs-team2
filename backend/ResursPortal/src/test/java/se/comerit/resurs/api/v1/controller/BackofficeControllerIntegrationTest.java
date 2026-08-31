package se.comerit.resurs.api.v1.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import se.comerit.resurs.security.WithCaseWorker;
import se.comerit.resurs.security.WithCompany;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:backoffice;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackofficeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    class applicationDetails {

        // so inner nested tests read as BackofficeController::applicationDetails::test
        @Nested
        class test {

            @Test
            void unauthenticatedIs401() throws Exception {
                mockMvc.perform(get("/api/v1/backoffice/application/1"))
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().contentTypeCompatibleWith("application/json"))
                        .andExpect(jsonPath("$.status").value(401))
                        .andExpect(jsonPath("$.title").value("Unauthorized"));
            }

            @Test
            @WithCompany
            void wrongRoleCompanyIs403() throws Exception {
                mockMvc.perform(get("/api/v1/backoffice/application/1"))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.status").value(403))
                        .andExpect(jsonPath("$.title").value("Access Denied"));
            }

            @Test
            @WithCaseWorker
            void invalidIdIs404() throws Exception {
                mockMvc.perform(get("/api/v1/backoffice/application/99999"))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.title").value("Application Not Found"))
                        .andExpect(jsonPath("$.detail", containsString("99999")));
            }

            @Test
            @WithCaseWorker(name = "Karin Handläggare")
            @Sql(statements = {
                    "DELETE FROM applications",
                    "DELETE FROM companies",
                    "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (100, '556000-9001', 'Nytt Bolag AB', 'Test Person')",
                    "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) VALUES (100, 100, 250000.00, 'Expansion av verksamheten', 'UNDER_REVIEW', NULL, NULL, NULL, '[]')"
            })
            void validIdWithoutDecisionOrDocuments() throws Exception {
                mockMvc.perform(get("/api/v1/backoffice/application/100"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.workerName").value("Karin Handläggare"))
                        .andExpect(jsonPath("$.application.id").value(100))
                        .andExpect(jsonPath("$.application.companyName").value("Nytt Bolag AB"))
                        .andExpect(jsonPath("$.application.orgNumber").value("556000-9001"))
                        .andExpect(jsonPath("$.application.purpose").value("Expansion av verksamheten"))
                        .andExpect(jsonPath("$.application.status").value("UNDER_REVIEW"))
                        .andExpect(jsonPath("$.application.decision").value(nullValue()))
                        .andExpect(jsonPath("$.documents").isEmpty());
            }

            @Test
            @WithCaseWorker
            @Sql(statements = {
                    "DELETE FROM applications",
                    "DELETE FROM companies",
                    "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (200, '556000-9002', 'Kredit Bolag AB', 'Test Person')",
                    "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) VALUES (200, 200, 250000.00, 'Utökad kredit', 'APPROVED', 'APPROVED', 'Godkänd', NULL, '[]')"
            })
            void validIdWithDecisionNoDocuments() throws Exception {
                mockMvc.perform(get("/api/v1/backoffice/application/200"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.application.status").value("APPROVED"))
                        .andExpect(jsonPath("$.application.decision").value("APPROVED"))
                        .andExpect(jsonPath("$.application.decisionReason").value("Godkänd"))
                        .andExpect(jsonPath("$.documents").isEmpty());
            }

            @Test
            @WithCaseWorker
            @Sql(statements = {
                    "DELETE FROM documents",
                    "DELETE FROM applications",
                    "DELETE FROM companies",
                    "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (300, '556000-9003', 'Lager Bolag AB', 'Test Person')",
                    "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) VALUES (300, 300, 250000.00, 'Lagerinvestering', 'PENDING_DOCS', NULL, NULL, NULL, '[]')",
                    "INSERT INTO documents (id, application_id, filename, doc_type) VALUES (100, 300, 'bokaplan.pdf', 'BOKFORING')",
                    "INSERT INTO documents (id, application_id, filename, doc_type) VALUES (101, 300, 'arsredovisning.pdf', 'ARSREDOVISNING')"
            })
            void validIdWithDocuments() throws Exception {
                mockMvc.perform(get("/api/v1/backoffice/application/300"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.documents.length()").value(2))
                        .andExpect(jsonPath("$.documents[*].filename",
                                containsInAnyOrder("bokaplan.pdf", "arsredovisning.pdf")))
                        .andExpect(jsonPath("$.documents[*].docType",
                                containsInAnyOrder("BOKFORING", "ARSREDOVISNING")));
            }

            @Test
            @WithCaseWorker(id = 2, name = "Anna Andersson", email = "anna@resurs.se")
            @Sql(statements = {
                    "DELETE FROM applications",
                    "DELETE FROM companies",
                    "INSERT INTO case_workers (id, name, email, password) VALUES (2, 'Anna Andersson', 'anna@resurs.se', 'unused')",
                    "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (100, '556000-9001', 'Nytt Bolag AB', 'Test Person')",
                    "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) VALUES (100, 100, 250000.00, 'Expansion av verksamheten', 'UNDER_REVIEW', NULL, NULL, NULL, '[]')"
            })
            void responseReflectsSecondaryUserFromAnnotation() throws Exception {
                mockMvc.perform(get("/api/v1/backoffice/application/100"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.workerName").value("Anna Andersson"))
                        .andExpect(jsonPath("$.application.id").value(100));
            }
        }
    }
}
