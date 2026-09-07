package se.comerit.resurs.api.v1.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;



import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.Company;
import tools.jackson.databind.ObjectMapper;

class AuditLogServiceTest {

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(new ObjectMapper());
    }

    private Application application() {
        Company company = new Company("556677-8899", "Testbolaget AB", "Kalle Kula");
        Application app = new Application(company, new BigDecimal("500000"), "Rörelsekapital");
        app.setAuditLog("[]");
        return app;
    }

    private Map<String, String> details(String key, String value) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    @Nested
    @DisplayName("Append persists the entry on the application")
    class Persistence {

        @Test
        @DisplayName("First append is persisted onto the application and returned")
        void firstAppendPersistsOnApplication() {
            Application app = application();

            String result = auditLogService.append(app, "APPLICATION_CREATED", details("orgNumber", "556677-8899"));

            assertTrue(result.contains("APPLICATION_CREATED"));
            assertTrue(result.contains("556677-8899"));
            assertEquals(result, app.getAuditLog());
            assertTrue(app.getAuditLog().startsWith("["));
            assertTrue(app.getAuditLog().endsWith("]"));
        }

        @Test
        @DisplayName("Multiple appends accumulate entries in order on the application")
        void multipleAppendsAccumulateInOrder() {
            Application app = application();

            auditLogService.append(app, "APPLICATION_CREATED", details("orgNumber", "556677-8899"));
            auditLogService.append(app, "SCORING_RUN", Map.of("result", "APPROVED", "flags", "0"));

            String log = app.getAuditLog();
            int first = log.indexOf("APPLICATION_CREATED");
            int second = log.indexOf("SCORING_RUN");
            assertTrue(first >= 0, "expected APPLICATION_CREATED entry");
            assertTrue(second > first, "scoring entry must come after created entry");
            assertTrue(log.contains("\"result\":\"APPROVED\""));
            assertTrue(log.contains("\"flags\":\"0\""));
        }
    }

    @Nested
    @DisplayName("Empty log handling")
    class EmptyLog {

        @Test
        @DisplayName("Null audit log is treated as empty")
        void nullAuditLogIsTreatedAsEmpty() {
            Application app = application();
            app.setAuditLog(null);

            String result = auditLogService.append(app, "APPLICATION_CREATED", details("orgNumber", "1"));

            assertTrue(result.startsWith("["), "null log should start a fresh array");
            assertEquals(result, app.getAuditLog());
        }

        @Test
        @DisplayName("Blank audit log is treated as empty")
        void blankAuditLogIsTreatedAsEmpty() {
            Application app = application();
            app.setAuditLog("   ");

            String result = auditLogService.append(app, "TEST_ACTION", Map.of());

            assertTrue(result.startsWith("["), "blank log should start a fresh array");
            assertEquals(result, app.getAuditLog());
        }
    }

    @Nested
    @DisplayName("Entry content")
    class EntryContent {

        @Test
        @DisplayName("Each entry carries a timestamp and the action")
        void entryCarriesTimestampAndAction() {
            String result = auditLogService.append(application(), "MANUAL_DECISION", details("decision", "APPROVED"));

            assertTrue(result.contains("\"action\":\"MANUAL_DECISION\""));
            assertTrue(result.contains("\"ts\":"));
        }

        @Test
        @DisplayName("Result is valid JSON with a single element for the first entry")
        void firstEntryIsSingleElementJsonArray() {
            String result = auditLogService.append(application(), "APPLICATION_CREATED", details("orgNumber", "123"));

            assertEquals('[', result.charAt(0));
            assertEquals(']', result.charAt(result.length() - 1));
            assertFalse(result.contains(",{"), "single entry must not contain a comma-separated second object");
        }
    }
}
