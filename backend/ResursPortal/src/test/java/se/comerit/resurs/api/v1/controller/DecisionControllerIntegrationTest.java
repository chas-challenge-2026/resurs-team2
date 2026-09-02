package se.comerit.resurs.api.v1.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import se.comerit.resurs.security.WithCaseWorker;
import se.comerit.resurs.security.WithCompany;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:decision;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DecisionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String DECISION_BODY = "{\"decision\":\"APPROVED\",\"comment\":\"OK\"}";

    @Nested
    class decide {

        @Test
        void unauthenticatedIs401() throws Exception {
            mockMvc.perform(post("/api/v1/applications/100/decision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(DECISION_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @WithCompany
        void wrongRoleCompanyIs403() throws Exception {
            mockMvc.perform(post("/api/v1/applications/100/decision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(DECISION_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Access Denied"));
        }

        @Test
        @WithCaseWorker(name = "Karin Handläggare")
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (500, '556000-9101', 'Beslut Bolag AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) VALUES (500, 500, 150000.00, 'Företagslån', 'UNDER_REVIEW', NULL, NULL, NULL, '[]')"
        })
        void approveApplication() throws Exception {
            mockMvc.perform(post("/api/v1/applications/500/decision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"APPROVED\",\"comment\":\"Godkänd\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(500))
                    .andExpect(jsonPath("$.companyName").value("Beslut Bolag AB"))
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.decision").value("APPROVED"))
                    .andExpect(jsonPath("$.decisionReason").value("Godkänd"));
        }

        @Test
        @WithCaseWorker(name = "Karin Handläggare")
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (501, '556000-9102', 'Avslag Bolag AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) VALUES (501, 501, 150000.00, 'Företagslån', 'UNDER_REVIEW', NULL, NULL, NULL, '[]')"
        })
        void rejectApplication() throws Exception {
            mockMvc.perform(post("/api/v1/applications/501/decision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"REJECTED\",\"comment\":\"Avslagen\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(501))
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.decision").value("REJECTED"))
                    .andExpect(jsonPath("$.decisionReason").value("Avslagen"));
        }

        @Test
        @WithCaseWorker(name = "Karin Handläggare")
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (502, '556000-9103', 'Tyst Bolag AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) VALUES (502, 502, 150000.00, 'Företagslån', 'UNDER_REVIEW', NULL, NULL, NULL, '[]')"
        })
        void approveWithoutComment() throws Exception {
            mockMvc.perform(post("/api/v1/applications/502/decision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"APPROVED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(502))
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.decision").value("APPROVED"))
                    .andExpect(jsonPath("$.decisionReason").value(nullValue()));
        }

        @Test
        @WithCaseWorker
        void missingDecisionIs400() throws Exception {
            mockMvc.perform(post("/api/v1/applications/100/decision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"comment\":\"OK\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithCaseWorker
        void nonExistentApplicationIs404() throws Exception {
            mockMvc.perform(post("/api/v1/applications/99999/decision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(DECISION_BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Application Not Found"))
                    .andExpect(jsonPath("$.detail", containsString("99999")));
        }

        @Test
        @WithCaseWorker
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (503, '556000-9104', 'Redan Beslutat AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) VALUES (503, 503, 150000.00, 'Företagslån', 'APPROVED', 'APPROVED', 'Godkänd', NULL, '[]')"
        })
        void alreadyDecidedIs409() throws Exception {
            mockMvc.perform(post("/api/v1/applications/503/decision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"REJECTED\",\"comment\":\"Försök igen\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.title").value("Application Already Decided"))
                    .andExpect(jsonPath("$.detail", containsString("503")));
        }
    }
}