package se.comerit.resurs.api.v1.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Nonnull;
import se.comerit.resurs.entity.Application;

/**
 * Helper for appending tamper-evident audit entries to an {@link Application}'s
 * {@code auditLog} JSON blob. The current implementation manipulates the JSON
 * string directly; a future plan replaces this with a dedicated audit table.
 */
@Service
public class AuditLogService {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ObjectMapper objectMapper;

    public AuditLogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Appends a single audit entry to the given application's log, persists it
     * on the application via {@link Application#setAuditLog}, and returns the
     * resulting full log JSON. The entry always carries a timestamp and an
     * {@code action}, plus any additional {@code details} supplied.
     */
    @Nonnull
    public String append(Application application, @Nonnull String action,
            @Nonnull Map<String, String> details) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ts", LocalDateTime.now().format(TS_FORMAT));
        fields.put("action", action);
        fields.putAll(details);

        String entry = toJson(fields);
        String currentLog = application.getAuditLog();
        String updatedLog;
        if (isEmptyLog(currentLog)) {
            updatedLog = newLog(entry);
        } else {
            updatedLog = appendEntry(currentLog, entry);
        }
        application.setAuditLog(updatedLog);
        return updatedLog;
    }

    /**
     * Returns {@code true} when the audit log has no entries yet, i.e. it is
     * {@code null}, blank, or the empty JSON array literal {@code []}.
     */
    private static boolean isEmptyLog(String currentLog) {
        return currentLog == null || currentLog.isBlank() || "[]".equals(currentLog.trim());
    }

    /**
     * Builds a new log containing only the given first entry (a valid JSON
     * array with a single element).
     */
    @Nonnull
    private static String newLog(String entry) {
        return "[" + entry + "]";
    }

    /**
     * Appends the given entry to the end of an existing non-empty audit log
     * JSON array by inserting it before the closing {@code ]}.
     */
    @Nonnull
    private static String appendEntry(String currentLog, String entry) {
        return currentLog.substring(0, currentLog.lastIndexOf("]")) + "," + entry + "]";
    }

    @Nonnull
    private String toJson(Map<String, String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit entry", e);
        }
    }
}
