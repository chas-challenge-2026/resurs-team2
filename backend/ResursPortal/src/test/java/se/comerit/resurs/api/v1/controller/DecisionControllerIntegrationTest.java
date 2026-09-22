package se.comerit.resurs.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;
import se.comerit.resurs.security.WithCaseWorker;
import se.comerit.resurs.security.WithCompany;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DecisionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

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
                "DELETE FROM audit_log",
                "DELETE FROM applications",
                "DELETE FROM case_workers",
                "DELETE FROM companies",
                "INSERT INTO case_workers (id, name, email, email_index, password) VALUES (1, 'Karin Handläggare', 'karin@resurs.se', X'01', 'x')",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (500, '556000-9101', 'Beslut Bolag AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, estimated_resolution_at) VALUES (500, 500, 150000.00, 'Företagslån', 'UNDER_REVIEW', NULL, NULL, NULL, '2026-09-22T10:00:00Z')"
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
                    .andExpect(jsonPath("$.decisionReason").value("Godkänd"))
                    // A decided application no longer carries an ETA.
                    .andExpect(jsonPath("$.estimatedResolutionAt").value(nullValue()));

            Application decided = applicationRepository.findById(500L).orElseThrow();
            assertThat(decided.getEstimatedResolutionAt()).isNull();

            // The clear is persisted as a valueless ETA_SET entry.
            List<AuditLog> logs = auditLogRepository.findByApplication(decided, Sort.by("sequenceNumber"));
            assertThat(logs).anySatisfy(log ->
                    assertThat(log.getEntry()).isEqualTo("{\"action\":\"ETA_SET\"}"));
        }

        @Test
        @WithCaseWorker(name = "Karin Handläggare")
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM audit_log",
                "DELETE FROM applications",
                "DELETE FROM case_workers",
                "DELETE FROM companies",
                "INSERT INTO case_workers (id, name, email, email_index, password) VALUES (1, 'Karin Handläggare', 'karin@resurs.se', X'01', 'x')",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (501, '556000-9102', 'Avslag Bolag AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result) VALUES (501, 501, 150000.00, 'Företagslån', 'UNDER_REVIEW', NULL, NULL, NULL)"
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
                "DELETE FROM audit_log",
                "DELETE FROM applications",
                "DELETE FROM case_workers",
                "DELETE FROM companies",
                "INSERT INTO case_workers (id, name, email, email_index, password) VALUES (1, 'Karin Handläggare', 'karin@resurs.se', X'01', 'x')",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (502, '556000-9103', 'Tyst Bolag AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result) VALUES (502, 502, 150000.00, 'Företagslån', 'UNDER_REVIEW', NULL, NULL, NULL)"
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
        @WithCaseWorker(name = "Karin Handläggare")
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM audit_log",
                "DELETE FROM applications",
                "DELETE FROM case_workers",
                "DELETE FROM companies",
                "INSERT INTO case_workers (id, name, email, email_index, password) VALUES (1, 'Karin Handläggare', 'karin@resurs.se', X'01', 'x')",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (510, '556000-9110', 'Assign Bolag AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result) VALUES (510, 510, 150000.00, 'Företagslån', 'UNDER_REVIEW', NULL, NULL, NULL)"
        })
        void approveApplicationAssignsAndAudits() throws Exception {
            mockMvc.perform(post("/api/v1/applications/510/decision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"APPROVED\",\"comment\":\"OK\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(510));

            var app = applicationRepository.findById(510L).orElseThrow();
            assertThat(app.getCaseWorker()).isNotNull();
            assertThat(app.getCaseWorker().getId()).isEqualTo(1L);

            java.util.List<AuditLog> logs = auditLogRepository.findByApplication(
                    app, Sort.by("sequenceNumber"));
            assertThat(logs).anySatisfy(log -> assertThat(log.getEntry())
                    .contains("\"action\":\"WORKER_ASSIGNED\"")
                    .contains("\"worker\":\"Karin Handläggare\""));
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
                "DELETE FROM audit_log",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (503, '556000-9104', 'Redan Beslutat AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result) VALUES (503, 503, 150000.00, 'Företagslån', 'APPROVED', 'APPROVED', 'Godkänd', NULL)"
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