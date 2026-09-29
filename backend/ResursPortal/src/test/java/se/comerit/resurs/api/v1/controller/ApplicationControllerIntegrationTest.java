package se.comerit.resurs.api.v1.controller;



import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



import java.util.List;



import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.api.Nested;

import org.junit.jupiter.api.Test;





import org.springframework.beans.factory.annotation.Autowired;



import org.springframework.boot.test.context.SpringBootTest;



import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;

import org.springframework.test.context.ActiveProfiles;

import org.springframework.test.context.TestPropertySource;

import org.springframework.test.context.jdbc.Sql;





import org.springframework.test.web.servlet.MockMvc;

import se.comerit.resurs.entity.Application;

import se.comerit.resurs.entity.AuditLog;

import se.comerit.resurs.repository.ApplicationRepository;

import se.comerit.resurs.repository.AuditLogRepository;

import se.comerit.resurs.security.WithCaseWorker;

import se.comerit.resurs.security.WithCompany;





@SpringBootTest(properties = {

&#x20;       "spring.datasource.url=jdbc:h2:mem:application;MODE=PostgreSQL"

})



@AutoConfigureMockMvc

@ActiveProfiles("test")

@TestPropertySource("/scoring-test.properties")

class ApplicationControllerIntegrationTest {



&#x20;   @Autowired

&#x20;   private MockMvc mockMvc;



&#x20;   @Autowired

&#x20;   private ApplicationRepository applicationRepository;



&#x20;   @Autowired

&#x20;   private AuditLogRepository auditLogRepository;



&#x20;   private static final String COMPANY_ORG = "556000-1234";



&#x20;   private static final String VALID_REQUEST_JSON = """

&#x20;           {

&#x20;             "equity": 500000.0,

&#x20;             "totalCapital": 1000000.0,

&#x20;             "currentAssets": 400000.0,

&#x20;             "currentLiabilities": 200000.0,

&#x20;             "totalLiabilities": 500000.0,

&#x20;             "operatingIncome": 150000.0,

&#x20;             "netRevenue": 1000000.0,

&#x20;             "requestedAmount": 300000,

&#x20;             "purpose": "Rörelsekapital",

&#x20;             "operatingCashFlow": 120000.0,

&#x20;             "investingCashFlow": -50000.0,

&#x20;             "interestExpenses": 20000.0,

&#x20;             "industry": "IT"

&#x20;           }

&#x20;           """;



&#x20;   private static String requestJsonWithAmount(long amount) {

&#x20;       return """

&#x20;               {

&#x20;                 "equity": 500000.0,

&#x20;                 "totalCapital": 1000000.0,

&#x20;                 "currentAssets": 400000.0,

&#x20;                 "currentLiabilities": 200000.0,

&#x20;                 "totalLiabilities": 500000.0,

&#x20;                 "operatingIncome": 150000.0,

&#x20;                 "netRevenue": 1000000.0,

&#x20;                 "requestedAmount": %d,

&#x20;                 "purpose": "Rörelsekapital",

&#x20;                 "operatingCashFlow": 120000.0,

&#x20;                 "investingCashFlow": -50000.0,

&#x20;                 "interestExpenses": 20000.0,

&#x20;                 "industry": "IT"

&#x20;               }

&#x20;               """.formatted(amount);

&#x20;   }



&#x20;   private static final String FINANCIAL_DATA =

&#x20;           "{\\"equity\\":500000.0,\\"totalCapital\\":1000000.0,\\"netRevenue\\":1000000.0,"

&#x20;           \+ "\\"requestedAmount\\":300000,\\"industry\\":\\"IT\\"}";



&#x20;   @Nested

&#x20;   @DisplayName("POST submit application")

&#x20;   class Submit {



&#x20;       @Test

&#x20;       void unauthenticatedIs401() throws Exception {

&#x20;           mockMvc.perform(post("/api/v1/applications")

&#x20;                   .contentType(MediaType.APPLICATION_JSON)

&#x20;                   .content(VALID_REQUEST_JSON))

&#x20;                   .andExpect(status().isUnauthorized())

&#x20;                   .andExpect(jsonPath("$.status").value(401))

&#x20;                   .andExpect(jsonPath("$.title").value("Unauthorized"));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCaseWorker

&#x20;       void wrongRoleCaseWorkerIs403() throws Exception {

&#x20;           mockMvc.perform(post("/api/v1/applications")

&#x20;                   .contentType(MediaType.APPLICATION_JSON)

&#x20;                   .content(VALID_REQUEST_JSON))

&#x20;                   .andExpect(status().isForbidden())

&#x20;                   .andExpect(jsonPath("$.status").value(403))

&#x20;                   .andExpect(jsonPath("$.title").value("Access Denied"));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (600, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')"

&#x20;       })

&#x20;       void submitsAndPersistsApplication() throws Exception {

&#x20;           mockMvc.perform(post("/api/v1/applications")

&#x20;                   .contentType(MediaType.APPLICATION_JSON)

&#x20;                   .content(VALID_REQUEST_JSON))

&#x20;                   .andExpect(status().isOk());



&#x20;           awaitScoringComplete();



&#x20;           assertThat(applicationRepository.findAll()).hasSize(1);

&#x20;           Application app = applicationRepository.findAll().get(0);

&#x20;           assertThat(app.getCompany().getOrgNumber()).isEqualTo(COMPANY_ORG);

&#x20;           assertThat(app.getPurpose()).isEqualTo("Rörelsekapital");

&#x20;           assertThat(app.getRequestedAmount()).isEqualByComparingTo("300000");



&#x20;           // Audit log table carries the full submit+score trail: the submit

&#x20;           // transaction wrote APPLICATION_CREATED and a valued ETA_SET

&#x20;           // synchronously; the async scoring appended SCORING_RUN and then a

&#x20;           // final ETA_SET reflecting its outcome (manual-review refresh or

&#x20;           // decision clear).

&#x20;           assertThat(auditLogRepository.findAll()).hasSize(4);

&#x20;           List\<String> entries = auditLogRepository.findAll().stream()

&#x20;                   .sorted((a, b) -> Long.compare(a.getSequenceNumber(), b.getSequenceNumber()))

&#x20;                   .map(AuditLog::getEntry)

&#x20;                   .toList();

&#x20;           assertThat(entries.get(0))

&#x20;                   .contains("\\"action\\":\\"APPLICATION_CREATED\\"")

&#x20;                   .contains("\\"orgNumber\\":\\"556000-1234\\"");

&#x20;           assertThat(entries.get(1))

&#x20;                   .contains("\\"action\\":\\"ETA_SET\\"")

&#x20;                   .contains("\\"estimatedResolutionAt\\":");

&#x20;           assertThat(entries.get(2)).contains("\\"action\\":\\"SCORING_RUN\\"");

&#x20;           assertThat(entries.get(3)).contains("\\"action\\":\\"ETA_SET\\"");



&#x20;           // A decision/reason should be produced by scoring.

&#x20;           assertThat(app.getDecisionReason()).isNotBlank();

&#x20;       }



&#x20;       private void awaitScoringComplete() throws InterruptedException {

&#x20;           for (int i = 0; i < 50; i++) {

&#x20;               List\<String> entries = auditLogRepository.findAll().stream()

&#x20;                       .map(AuditLog::getEntry)

&#x20;                       .toList();

&#x20;               boolean scoringRun = entries.stream().anyMatch(entry -> entry.contains("SCORING_RUN"));

&#x20;               // The async run writes SCORING_RUN and its outcome ETA_SET as

&#x20;               // separate repository transactions; wait for both so the trail

&#x20;               // assertions never race the second commit.

&#x20;               boolean outcomeEtaSet = entries.stream()

&#x20;                       .filter(entry -> entry.contains("\\"action\\":\\"ETA_SET\\""))

&#x20;                       .count() >= 2;

&#x20;               if (scoringRun && outcomeEtaSet) {

&#x20;                   return;

&#x20;               }

&#x20;               Thread.sleep(200);

&#x20;           }

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany(orgNumber = "556000-9999")

&#x20;       void missingCompanyIs400() throws Exception {

&#x20;           mockMvc.perform(post("/api/v1/applications")

&#x20;                   .contentType(MediaType.APPLICATION_JSON)

&#x20;                   .content(VALID_REQUEST_JSON))

&#x20;                   .andExpect(status().isBadRequest());

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (601, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')"

&#x20;       })

&#x20;       void requestedAmountBelowMinimumIs400() throws Exception {

&#x20;           mockMvc.perform(post("/api/v1/applications")

&#x20;                   .contentType(MediaType.APPLICATION_JSON)

&#x20;                   .content(requestJsonWithAmount(49999)))

&#x20;                   .andExpect(status().isBadRequest());



&#x20;           assertThat(applicationRepository.findAll()).isEmpty();

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (602, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')"

&#x20;       })

&#x20;       void requestedAmountAboveMaximumIs400() throws Exception {

&#x20;           mockMvc.perform(post("/api/v1/applications")

&#x20;                   .contentType(MediaType.APPLICATION_JSON)

&#x20;                   .content(requestJsonWithAmount(10000001)))

&#x20;                   .andExpect(status().isBadRequest());



&#x20;           assertThat(applicationRepository.findAll()).isEmpty();

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (603, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')"

&#x20;       })

&#x20;       void requestedAmountAtMinimumIsAccepted() throws Exception {

&#x20;           mockMvc.perform(post("/api/v1/applications")

&#x20;                   .contentType(MediaType.APPLICATION_JSON)

&#x20;                   .content(requestJsonWithAmount(50000)))

&#x20;                   .andExpect(status().isOk());



&#x20;           // Wait for the async scoring so no in-flight thread races the next

&#x20;           // test's table cleanup with a late SCORING_RUN audit insert.

&#x20;           awaitScoringComplete();



&#x20;           assertThat(applicationRepository.findAll()).hasSize(1);

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (604, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')"

&#x20;       })

&#x20;       void requestedAmountAtMaximumIsAccepted() throws Exception {

&#x20;           mockMvc.perform(post("/api/v1/applications")

&#x20;                   .contentType(MediaType.APPLICATION_JSON)

&#x20;                   .content(requestJsonWithAmount(10000000)))

&#x20;                   .andExpect(status().isOk());



&#x20;           // Wait for the async scoring so no in-flight thread races the next

&#x20;           // test's table cleanup with a late SCORING_RUN audit insert.

&#x20;           awaitScoringComplete();



&#x20;           assertThat(applicationRepository.findAll()).hasSize(1);

&#x20;       }

&#x20;   }



&#x20;   @Nested

&#x20;   @DisplayName("GET view application")

&#x20;   class ViewApplication {



&#x20;       @Test

&#x20;       void unauthenticatedIs401() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications/1"))

&#x20;                   .andExpect(status().isUnauthorized())

&#x20;                   .andExpect(jsonPath("$.status").value(401))

&#x20;                   .andExpect(jsonPath("$.title").value("Unauthorized"));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany

&#x20;       void nonExistentApplicationIs404() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications/99999"))

&#x20;                   .andExpect(status().isNotFound())

&#x20;                   .andExpect(jsonPath("$.status").value(404))

&#x20;                   .andExpect(jsonPath("$.title").value("Application Not Found"));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany(orgNumber = "556000-1234")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (700, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result) VALUES (700, 700, 300000.00, 'Rörelsekapital', 'UNDER_REVIEW', NULL, NULL, NULL)",

&#x20;               "INSERT INTO documents (uuid, application_id, filename, doc_type) VALUES ('00000000-0000-0000-0000-000000000700', 700, 'bokaplan.pdf', 'BOKFORING')"

&#x20;       })

&#x20;       void companyCanViewOwnApplication() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications/700"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.application.id").value(700))

&#x20;                   .andExpect(jsonPath("$.application.companyName").value("Malmö Fastigheter AB"))

&#x20;                   .andExpect(jsonPath("$.application.orgNumber").value("556000-1234"))

&#x20;                   .andExpect(jsonPath("$.application.purpose").value("Rörelsekapital"))

&#x20;                   .andExpect(jsonPath("$.application.status").value("UNDER_REVIEW"))

&#x20;                   .andExpect(jsonPath("$.workerName").doesNotExist())

&#x20;                   .andExpect(jsonPath("$.documents.length()").value(1))

&#x20;                   .andExpect(jsonPath("$.documents[0].filename").value("bokaplan.pdf"))

&#x20;                   .andExpect(jsonPath("$.documents[0].docType").value("BOKFORING"));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany(orgNumber = "556000-9999")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (701, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Ägarens Bolag AB', 'Test Person')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result) VALUES (701, 701, 300000.00, 'Rörelsekapital', 'UNDER_REVIEW', NULL, NULL, NULL)"

&#x20;       })

&#x20;       void companyCannotViewAnotherCompanysApplication() throws Exception {

&#x20;           // The authenticated company (556000-9999) must NOT be able to see

&#x20;           // an application owned by a different company (556000-1234). It

&#x20;           // should be indistinguishable from a missing application.

&#x20;           mockMvc.perform(get("/api/v1/applications/701"))

&#x20;                   .andExpect(status().isNotFound())

&#x20;                   .andExpect(jsonPath("$.status").value(404))

&#x20;                   .andExpect(jsonPath("$.title").value("Application Not Found"));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCaseWorker(name = "Karin Handläggare")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM case_workers",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO case_workers (id, name, email, email_index, password) VALUES (1, 'Karin Handläggare', 'karin\@resurs.se', X'01', 'x')",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (702, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result) VALUES (702, 702, 400000.00, 'Expansion', 'APPROVED', 'APPROVED', 'Godkänd', NULL)"

&#x20;       })

&#x20;       void caseWorkerCanViewAnyApplication() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications/702"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.application.id").value(702))

&#x20;                   .andExpect(jsonPath("$.application.companyName").value("Malmö Fastigheter AB"))

&#x20;                   .andExpect(jsonPath("$.application.status").value("APPROVED"))

&#x20;                   .andExpect(jsonPath("$.application.decision").value("APPROVED"))

&#x20;                   .andExpect(jsonPath("$.application.decisionReason").value("Godkänd"))

&#x20;                   .andExpect(jsonPath("$.workerName").value("Karin Handläggare"))

&#x20;                   .andExpect(jsonPath("$.documents").isEmpty());

&#x20;       }



&#x20;       @Test

&#x20;       @WithCaseWorker(name = "Karin Handläggare")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM case_workers",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO case_workers (id, name, email, email_index, password) VALUES (1, 'Karin Handläggare', 'karin\@resurs.se', X'01', 'x')",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (760, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status, financial_data) VALUES (760, 760, 300000.00, 'Rörelsekapital', 'UNDER_REVIEW', '" + FINANCIAL_DATA + "')"

&#x20;       })

&#x20;       void caseWorkerCanSeeFinancialData() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications/760"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.application.id").value(760))

&#x20;                   .andExpect(jsonPath("$.financialData").value(FINANCIAL_DATA));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany(orgNumber = "556000-1234")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (770, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status, financial_data) VALUES (770, 770, 300000.00, 'Rörelsekapital', 'UNDER_REVIEW', '" + FINANCIAL_DATA + "')"

&#x20;       })

&#x20;       void companyCannotSeeFinancialData() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications/770"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.application.id").value(770))

&#x20;                   .andExpect(jsonPath("$.financialData").doesNotExist());

&#x20;       }



&#x20;       @Test

&#x20;       @WithCaseWorker(name = "Karin Handläggare")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM case_workers",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO case_workers (id, name, email, email_index, password) VALUES (1, 'Karin Handläggare', 'karin\@resurs.se', X'01', 'x')",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (780, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (780, 780, 300000.00, 'Rörelsekapital', 'UNDER_REVIEW')"

&#x20;       })

&#x20;       void caseWorkerSeesNoFinancialDataWhenNoneStored() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications/780"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.application.id").value(780))

&#x20;                   .andExpect(jsonPath("$.financialData").doesNotExist());

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany(orgNumber = "556000-1234")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (703, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result) VALUES (703, 703, 400000.00, 'Expansion', 'REJECTED', 'REJECTED', 'Automatiskt avslag', NULL)"

&#x20;       })

&#x20;       void companyViewsOwnAutoRejectedApplicationHasNullWorkerName() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications/703"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.application.id").value(703))

&#x20;                   .andExpect(jsonPath("$.application.status").value("REJECTED"))

&#x20;                   .andExpect(jsonPath("$.workerName").doesNotExist());

&#x20;       }



&#x20;       @Test

&#x20;       @WithCaseWorker(name = "Karin Handläggare")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM case_workers",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO case_workers (id, name, email, email_index, password) VALUES (1, 'Karin Handläggare', 'karin\@resurs.se', X'01', 'x')",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (704, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (704, 704, 400000.00, 'Expansion', 'UNDER_REVIEW')"

&#x20;       })

&#x20;       void firstViewerIsAssignedAndWorkerNameReturned() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications/704"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.workerName").value("Karin Handläggare"));



&#x20;           Application app = applicationRepository.findById(704L).orElseThrow();

&#x20;           assertThat(app.getCaseWorker()).isNotNull();

&#x20;           assertThat(app.getCaseWorker().getId()).isEqualTo(1L);

&#x20;       }



&#x20;       @Test

&#x20;       @WithCaseWorker(id = 2, name = "Oskar Granskare")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM case_workers",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO case_workers (id, name, email, email_index, password) VALUES (1, 'Karin Handläggare', 'karin\@resurs.se', X'01', 'x')",

&#x20;               "INSERT INTO case_workers (id, name, email, email_index, password) VALUES (2, 'Oskar Granskare', 'oskar\@resurs.se', X'02', 'x')",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (705, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Test Person')",

&#x20;               "INSERT INTO applications (id, company_id, case_worker_id, requested_amount, purpose, status) VALUES (705, 705, 1, 400000.00, 'Expansion', 'UNDER_REVIEW')"

&#x20;       })

&#x20;       void assignedWorkerIsNotReplacedBySecondViewer() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications/705"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.workerName").value("Karin Handläggare"));



&#x20;           Application app = applicationRepository.findById(705L).orElseThrow();

&#x20;           assertThat(app.getCaseWorker()).isNotNull();

&#x20;           assertThat(app.getCaseWorker().getId()).isEqualTo(1L);

&#x20;       }



&#x20;       @Test

&#x20;       @WithCaseWorker

&#x20;       void caseWorkerNonExistentApplicationIs404() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications/99999"))

&#x20;                   .andExpect(status().isNotFound())

&#x20;                   .andExpect(jsonPath("$.status").value(404))

&#x20;                   .andExpect(jsonPath("$.title").value("Application Not Found"));

&#x20;       }

&#x20;   }



&#x20;   @Nested

&#x20;   @DisplayName("GET list applications")

&#x20;   class ListApplications {



&#x20;       @Test

&#x20;       void unauthenticatedIs401() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications"))

&#x20;                   .andExpect(status().isUnauthorized())

&#x20;                   .andExpect(jsonPath("$.status").value(401))

&#x20;                   .andExpect(jsonPath("$.title").value("Unauthorized"));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCaseWorker

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (800, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Company A', 'Test')",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (801, '556000-5678', X'a4f37788064f1cf726eadc704db91cdc0b1513e482981ff59641e13f518bbbea', 'Company B', 'Test')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (810, 800, 300000.00, 'App A', 'UNDER_REVIEW')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (811, 801, 300000.00, 'App B', 'UNDER_REVIEW')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (812, 800, 300000.00, 'App C', 'APPROVED')"

&#x20;       })

&#x20;       void caseWorkerSeesOnlyUnderReviewApplications() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.content.length()").value(2))

&#x20;                   .andExpect(jsonPath("$.content[0].id").value(810))

&#x20;                   .andExpect(jsonPath("$.content[1].id").value(811))

&#x20;                   .andExpect(jsonPath("$.content[0].status").value("UNDER_REVIEW"))

&#x20;                   .andExpect(jsonPath("$.content[1].status").value("UNDER_REVIEW"))

&#x20;                   .andExpect(jsonPath("$.page").value(0))

&#x20;                   .andExpect(jsonPath("$.size").value(20))

&#x20;                   .andExpect(jsonPath("$.totalElements").value(2))

&#x20;                   .andExpect(jsonPath("$.totalPages").value(1))

&#x20;                   .andExpect(jsonPath("$.first").value(true))

&#x20;                   .andExpect(jsonPath("$.last").value(true));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCaseWorker

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (820, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Company A', 'Test')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (830, 820, 300000.00, 'App X', 'APPROVED')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (831, 820, 300000.00, 'App Y', 'REJECTED')"

&#x20;       })

&#x20;       void caseWorkerWithNoPendingApplicationsSeesEmptyList() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.content.length()").value(0))

&#x20;                   .andExpect(jsonPath("$.totalElements").value(0))

&#x20;                   .andExpect(jsonPath("$.totalPages").value(0))

&#x20;                   .andExpect(jsonPath("$.page").value(0))

&#x20;                   .andExpect(jsonPath("$.size").value(20));



&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany(orgNumber = "556000-1234")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (840, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Company A', 'Test')",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (841, '556000-5678', X'a4f37788064f1cf726eadc704db91cdc0b1513e482981ff59641e13f518bbbea', 'Company B', 'Test')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (850, 840, 300000.00, 'Own Under Review', 'UNDER_REVIEW')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (851, 840, 300000.00, 'Own Approved', 'APPROVED')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (852, 841, 300000.00, 'Other Company', 'UNDER_REVIEW')"

&#x20;       })

&#x20;       void companySeesOnlyItsOwnApplications() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.content.length()").value(2))

&#x20;                   .andExpect(jsonPath("$[\*].orgNumber").value(org.hamcrest.Matchers.everyItem(

&#x20;                           org.hamcrest.Matchers.is(COMPANY_ORG))))

&#x20;                   .andExpect(jsonPath("$.totalElements").value(2))

&#x20;                   .andExpect(jsonPath("$.totalPages").value(1));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany(orgNumber = "556000-1234")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (860, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Company A', 'Test')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (870, 860, 300000.00, 'Under Review', 'UNDER_REVIEW')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (871, 860, 300000.00, 'Approved', 'APPROVED')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (872, 860, 300000.00, 'Rejected', 'REJECTED')"

&#x20;       })

&#x20;       void companySeesAllOwnApplicationsRegardlessOfStatus() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.content.length()").value(3))

&#x20;                   .andExpect(jsonPath("$.content[\*].status").value(

&#x20;                           org.hamcrest.Matchers.hasItems("UNDER_REVIEW", "APPROVED", "REJECTED")))

&#x20;                   .andExpect(jsonPath("$.totalElements").value(3))

&#x20;                   .andExpect(jsonPath("$.totalPages").value(1));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany(orgNumber = "556000-9999")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (880, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Company A', 'Test')",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (881, '556000-9999', X'f3449df24d42bdb4f840559a40456274c4828b6a115838e6d588e89296eeb1fc', 'Company With No Apps', 'Test')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (890, 880, 300000.00, 'App', 'UNDER_REVIEW')"

&#x20;       })

&#x20;       void companyWithNoApplicationsSeesEmptyList() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.content.length()").value(0));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCaseWorker

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (900, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Company A', 'Test')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (910, 900, 300000.00, 'Under Review', 'UNDER_REVIEW')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (911, 900, 300000.00, 'Approved', 'APPROVED')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (912, 900, 300000.00, 'Rejected', 'REJECTED')"

&#x20;       })

&#x20;       void caseWorkerCanFilterByStatus() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications").param("status", "APPROVED"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.content.length()").value(1))

&#x20;                   .andExpect(jsonPath("$.content[0].id").value(911))

&#x20;                   .andExpect(jsonPath("$.content[0].status").value("APPROVED"))

&#x20;                   .andExpect(jsonPath("$.totalElements").value(1))

&#x20;                   .andExpect(jsonPath("$.totalPages").value(1));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCaseWorker

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (920, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Company A', 'Test')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (930, 920, 300000.00, 'A', 'UNDER_REVIEW')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (931, 920, 300000.00, 'B', 'APPROVED')"

&#x20;       })

&#x20;       void caseWorkerFilterUnderReviewMatchesDefault() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications").param("status", "UNDER_REVIEW"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.content.length()").value(1))

&#x20;                   .andExpect(jsonPath("$.content[0].status").value("UNDER_REVIEW"))

&#x20;                   .andExpect(jsonPath("$.totalElements").value(1))

&#x20;                   .andExpect(jsonPath("$.totalPages").value(1));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany(orgNumber = "556000-1234")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (940, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Company A', 'Test')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (950, 940, 300000.00, 'Under Review', 'UNDER_REVIEW')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (951, 940, 300000.00, 'Approved', 'APPROVED')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (952, 940, 300000.00, 'Rejected', 'REJECTED')"

&#x20;       })

&#x20;       void companyCanFilterByStatus() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications").param("status", "APPROVED"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.content.length()").value(1))

&#x20;                   .andExpect(jsonPath("$.content[0].id").value(951))

&#x20;                   .andExpect(jsonPath("$.content[0].status").value("APPROVED"))

&#x20;                   .andExpect(jsonPath("$.totalElements").value(1))

&#x20;                   .andExpect(jsonPath("$.totalPages").value(1));

&#x20;       }



&#x20;       @Test

&#x20;       @WithCompany(orgNumber = "556000-1234")

&#x20;       @Sql(statements = {

&#x20;               "DELETE FROM documents",

&#x20;               "DELETE FROM audit_log",

&#x20;               "DELETE FROM applications",

&#x20;               "DELETE FROM companies",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (960, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Company A', 'Test')",

&#x20;               "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (961, '556000-5678', X'a4f37788064f1cf726eadc704db91cdc0b1513e482981ff59641e13f518bbbea', 'Company B', 'Test')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (970, 960, 300000.00, 'Own', 'UNDER_REVIEW')",

&#x20;               "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (971, 961, 300000.00, 'Other', 'UNDER_REVIEW')"

&#x20;       })

&#x20;       void companyFilterDoesNotLeakOtherCompaniesApplications() throws Exception {

&#x20;           mockMvc.perform(get("/api/v1/applications").param("status", "UNDER_REVIEW"))

&#x20;                   .andExpect(status().isOk())

&#x20;                   .andExpect(jsonPath("$.content.length()").value(1))

&#x20;                   .andExpect(jsonPath("$.content[0].id").value(970))

&#x20;                   .andExpect(jsonPath("$.content[0].orgNumber").value(COMPANY_ORG));

&#x20;       }

&#x20;   }

}