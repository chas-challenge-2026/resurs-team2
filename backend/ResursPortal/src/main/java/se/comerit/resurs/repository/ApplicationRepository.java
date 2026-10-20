package se.comerit.resurs.repository;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.CaseWorker;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    Page<Application> findByCompanyId(Long companyId, Pageable pageable);
    Page<Application> findByCompanyIdAndStatus(Long companyId, ApplicationStatus status, Pageable pageable);
    Page<Application> findByStatus(ApplicationStatus status, Pageable pageable);

    /**
     * Locks the application row for the duration of the current transaction.
     *
     * <p>Blocks until the row is available, so callers that need a consistent view of
     * related rows can hold it across several reads and writes. Must be called inside a
     * transaction; without one the lock is released before the caller can use the result.
     *
     * @return the locked application, or empty if no application has that id
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Application a WHERE a.id = :id")
    Optional<Application> lockById(@Param("id") Long id);

    @Query("SELECT a FROM Application a JOIN FETCH a.company WHERE a.status = :status ORDER BY a.createdAt ASC")
    Page<Application> findByStatusOrderByCreatedAtAsc(@Param("status") ApplicationStatus status, Pageable pageable);

    @Query("SELECT a FROM Application a JOIN FETCH a.company WHERE a.status IN (:statuses) ORDER BY a.updatedAt DESC")
    Page<Application> findByStatusInOrderByUpdatedAtDesc(@Param("statuses") Collection<ApplicationStatus> statuses, Pageable pageable);

    @Query("SELECT a FROM Application a JOIN FETCH a.company LEFT JOIN FETCH a.documents LEFT JOIN FETCH a.caseWorker WHERE a.id = :id")
    Optional<Application> findByIdWithDocuments(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Application a SET a.caseWorker = :worker WHERE a.id = :id AND a.caseWorker IS NULL")
    int assignIfUnassigned(@Param("id") Long id, @Param("worker") CaseWorker worker);
}
