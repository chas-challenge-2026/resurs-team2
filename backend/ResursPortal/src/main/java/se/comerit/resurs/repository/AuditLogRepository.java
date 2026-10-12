package se.comerit.resurs.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByApplication(Application application, Sort sort);

    @Query("SELECT COALESCE(MAX(a.sequenceNumber), 0) FROM AuditLog a WHERE a.application = :app")
    long getNextSequenceNumber(@Param("app") Application app);

    Optional<AuditLog> findTopByApplicationOrderBySequenceNumberDesc(Application app);

    /**
     * Chain hash of the most recent entry for an application, as raw 32 bytes, or empty if
     * the application has no entries yet.
     *
     * <p>Callers that sign a new entry must pass {@code null}, not an empty or zero-filled
     * array, when this is empty, to start a new chain.
     */
    @Query("SELECT a.hash FROM AuditLog a WHERE a.application = :app ORDER BY a.sequenceNumber DESC LIMIT 1")
    Optional<byte[]> findPreviousHash(@Param("app") Application app);
}
