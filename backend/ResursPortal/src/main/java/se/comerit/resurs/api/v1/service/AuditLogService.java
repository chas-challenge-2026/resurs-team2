package se.comerit.resurs.api.v1.service;

import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;
import se.comerit.resurs.audit.AuditEntry;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.repository.AuditLogRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Nonnull
    public AuditLog append(Application application, @Nonnull AuditEntry entry) {
        long seq = auditLogRepository.getNextSequenceNumber(application) + 1;
        String prevHash = auditLogRepository.findPreviousHash(application).orElse("");

        String entryJson = toJson(entry);
        String hash = computeHash(prevHash, entryJson);

        AuditLog log = new AuditLog(application, seq, hash, prevHash, entryJson);
        return auditLogRepository.save(log);
    }

    @Nonnull
    private String computeHash(@Nonnull String previousHash, @Nonnull String entry) {
        // TODO: Implement actual hash computation
        return "";
    }

    @Nonnull
    private String toJson(AuditEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize audit entry", e);
        }
    }
}
