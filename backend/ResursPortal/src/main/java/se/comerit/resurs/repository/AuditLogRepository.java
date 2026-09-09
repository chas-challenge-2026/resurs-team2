package se.comerit.resurs.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT COALESCE(MAX(a.sequenceNumber), 0) FROM AuditLog a WHERE a.application = :app")
    long getNextSequenceNumber(@Param("app") Application app);

    Optional<AuditLog> findTopByApplicationOrderBySequenceNumberDesc(Application app);
}
