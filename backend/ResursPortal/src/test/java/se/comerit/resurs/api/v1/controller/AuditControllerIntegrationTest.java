package se.comerit.resurs.api.v1.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import se.comerit.resurs.security.WithCaseWorker;
import se.comerit.resurs.security.WithCompany;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    class getAuditLogs {

        @Test
        void unauthenticatedIs401() throws Exception {
            mockMvc.perform(get("/api/v1/applications/600/audit-log"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @WithCaseWorker
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM audit_log",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (600, '556000-1234', 'Audit Bolag AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (600, 600, 150000.00, 'Företagslån', 'UNDER_REVIEW')",
                "INSERT INTO audit_log (application_id, sequence_number, hash, previous_hash, entry, timestamp) VALUES (600, 1, '', '', '{\"action\":\"APPLICATION_CREATED\",\"orgNumber\":\"556000-1234\"}', '2026-01-15T10:00:05')",
                "INSERT INTO audit_log (application_id, sequence_number, hash, previous_hash, entry, timestamp) VALUES (600, 2, '', '', '{\"action\":\"SCORING_RUN\",\"result\":\"REVIEW\",\"flags\":\"1\"}', '2026-01-15T10:00:03')",
                "INSERT INTO audit_log (application_id, sequence_number, hash, previous_hash, entry, timestamp) VALUES (600, 3, '', '', '{\"action\":\"MANUAL_DECISION\",\"decision\":\"APPROVED\",\"worker\":\"Karin Handläggare\"}', '2026-01-15T10:00:01')"
        })
        void returnsAllLogsInSequenceOrderByDefault() throws Exception {
            mockMvc.perform(get("/api/v1/applications/600/audit-log"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].sequenceNumber").value(1))
                    .andExpect(jsonPath("$[0].entry.action").value("APPLICATION_CREATED"))
                    .andExpect(jsonPath("$[1].sequenceNumber").value(2))
                    .andExpect(jsonPath("$[1].entry.action").value("SCORING_RUN"))
                    .andExpect(jsonPath("$[2].sequenceNumber").value(3))
                    .andExpect(jsonPath("$[2].entry.action").value("MANUAL_DECISION"));
        }

        @Test
        @WithCaseWorker
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM audit_log",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (600, '556000-1234', 'Audit Bolag AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (600, 600, 150000.00, 'Företagslån', 'UNDER_REVIEW')",
                "INSERT INTO audit_log (application_id, sequence_number, hash, previous_hash, entry, timestamp) VALUES (600, 1, '', '', '{\"action\":\"APPLICATION_CREATED\"}', '2026-01-15T10:00:05')",
                "INSERT INTO audit_log (application_id, sequence_number, hash, previous_hash, entry, timestamp) VALUES (600, 2, '', '', '{\"action\":\"SCORING_RUN\"}', '2026-01-15T10:00:03')",
                "INSERT INTO audit_log (application_id, sequence_number, hash, previous_hash, entry, timestamp) VALUES (600, 3, '', '', '{\"action\":\"MANUAL_DECISION\"}', '2026-01-15T10:00:01')"
        })
        void supportsSequenceDescendingSort() throws Exception {
            mockMvc.perform(get("/api/v1/applications/600/audit-log")
                            .param("sort", "SEQUENCE_DESC"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sequenceNumber").value(3))
                    .andExpect(jsonPath("$[1].sequenceNumber").value(2))
                    .andExpect(jsonPath("$[2].sequenceNumber").value(1));
        }

        @Test
        @WithCaseWorker
        @Sql(statements = {
                "DELETE FROM documents",
                "DELETE FROM audit_log",
                "DELETE FROM applications",
                "DELETE FROM companies",
                "INSERT INTO companies (id, org_number, company_name, authorized_signatory) VALUES (600, '556000-1234', 'Audit Bolag AB', 'Test Person')",
                "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (600, 600, 150000.00, 'Företagslån', 'UNDER_REVIEW')",
                "INSERT INTO audit_log (application_id, sequence_number, hash, previous_hash, entry, timestamp) VALUES (600, 1, '', '', '{\"action\":\"APPLICATION_CREATED\"}', '2026-01-15T10:00:05')",
                "INSERT INTO audit_log (application_id, sequence_number, hash, previous_hash, entry, timestamp) VALUES (600, 2, '', '', '{\"action\":\"SCORING_RUN\"}', '2026-01-15T10:00:03')",
                "INSERT INTO audit_log (application_id, sequence_number, hash, previous_hash, entry, timestamp) VALUES (600, 3, '', '', '{\"action\":\"MANUAL_DECISION\"}', '2026-01-15T10:00:01')"
        })
        void supportsTimestampAscendingSortIndependentOfSequence() throws Exception {
            mockMvc.perform(get("/api/v1/applications/600/audit-log")
                            .param("sort", "TIMESTAMP_ASC"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sequenceNumber").value(3))
                    .andExpect(jsonPath("$[1].sequenceNumber").value(2))
                    .andExpect(jsonPath("$[2].sequenceNumber").value(1));
        }

        @Test
        @WithCompany
        void companyIsForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/applications/600/audit-log"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Access Denied"));
        }

        @Test
        @WithCaseWorker
        void nonExistentApplicationIs404() throws Exception {
            mockMvc.perform(get("/api/v1/applications/99999/audit-log"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Application Not Found"))
                    .andExpect(jsonPath("$.detail", containsString("99999")));
        }
    }
}