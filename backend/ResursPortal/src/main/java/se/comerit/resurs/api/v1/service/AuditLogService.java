package se.comerit.resurs.api.v1.service;

import java.util.List;

import jakarta.annotation.Nonnull;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.resurs.audit.AuditEntry;
import se.comerit.resurs.api.v1.dto.AuditLogResponse;
import se.comerit.resurs.api.v1.dto.AuditSort;
import se.comerit.resurs.api.v1.mapper.ApplicationMapper;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;
import se.comerit.resurs.security.CompanyPrincipal;
import se.comerit.resurs.security.UserPrincipal;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final AuditSigningService signer;

    public AuditLogService(AuditLogRepository auditLogRepository, ApplicationRepository applicationRepository,
            ObjectMapper objectMapper, AuditSigningService signer) {
        this.auditLogRepository = auditLogRepository;
        this.applicationRepository = applicationRepository;
        this.objectMapper = objectMapper;
        this.signer = signer;
    }

    /**
     * Appends one signed entry to an application's audit chain.
     *
     * <p>The entry JSON is signed together with the predecessor's hash, so the stored
     * hash and signature commit to both the content and the position of the entry in
     * the chain. Altering an entry, or reordering it, breaks the chain at that point.
     *
     * <p>Concurrent appends to the same application are serialised, so each entry is
     * assigned the next sequence number and chained to the entry before it. Appending to
     * different applications is unaffected.
     *
     * <p>Runs in the caller's transaction when there is one, so an append is committed or
     * rolled back together with the state change it records. Without an enclosing
     * transaction this method commits on its own.
     *
     * @return the saved entry, carrying its assigned sequence number
     * @throws org.springframework.dao.DataIntegrityViolationException if the chain's
     *         uniqueness invariant has been violated by some other writer
     */
    @Nonnull
    @Transactional
    public AuditLog append(Application application, @Nonnull AuditEntry entry) {
        applicationRepository.lockById(application.getId());

        String entryJson = toJson(entry);
        long seq = auditLogRepository.getNextSequenceNumber(application) + 1;
        // Empty means the application has no entries yet: sign a new chain from genesis
        // rather than chaining from a zero-length "link".
        byte[] prevHash = auditLogRepository.findPreviousHash(application).orElse(null);

        AuditSigningService.SignedEntry signed = signer.signEntry(entryJson, prevHash);
        return auditLogRepository.save(
                new AuditLog(application, seq, signed.hash(), signed.signature(), entryJson));
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
     * Returns the audit log for an application. A case worker may read any
     * application's log; a company may only read its own. For any application
     * the caller is not allowed to see, or that does not exist, an
     * {@link ApplicationNotFoundException} is thrown so that the existence of
     * other applications is not leaked.
     */
    @Nonnull
    public List<AuditLogResponse> listAuditLogs(@Nonnull Long applicationId, @Nonnull AuditSort sort,
            @Nonnull UserPrincipal principal) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (principal instanceof CompanyPrincipal company
                && !application.getCompany().getOrgNumber().equals(company.orgNumber())) {
            throw new ApplicationNotFoundException(applicationId);
        }

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
