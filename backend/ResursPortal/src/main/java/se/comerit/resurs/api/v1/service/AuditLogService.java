package se.comerit.resurs.api.v1.service;

import java.util.List;

import jakarta.annotation.Nonnull;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import se.comerit.resurs.audit.AuditEntry;
import se.comerit.resurs.api.v1.dto.AuditLogResponse;
import se.comerit.resurs.api.v1.dto.AuditSort;
import se.comerit.resurs.api.v1.mapper.ApplicationMapper;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ApplicationRepository applicationRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.applicationRepository = applicationRepository;
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

    /**
     * Returns the audit log for an application, ordered by the requested sort.
     * Throws an {@link ApplicationNotFoundException} if the application does
     * not exist.
     */
    @Nonnull
    public List<AuditLogResponse> listAuditLogs(@Nonnull Long applicationId, @Nonnull AuditSort sort) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        Sort order = switch (sort) {
            case SEQUENCE_ASC -> Sort.by(Sort.Direction.ASC, "sequenceNumber");
            case SEQUENCE_DESC -> Sort.by(Sort.Direction.DESC, "sequenceNumber");
            case TIMESTAMP_ASC -> Sort.by(Sort.Direction.ASC, "timestamp");
            case TIMESTAMP_DESC -> Sort.by(Sort.Direction.DESC, "timestamp");
        };

        return auditLogRepository.findByApplication(application, order).stream()
                .map(ApplicationMapper::toAuditLogResponse)
                .toList();
    }
}
