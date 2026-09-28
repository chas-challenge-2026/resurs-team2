package se.comerit.resurs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.CaseWorker;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByCompanyId(Long companyId);
    List<Application> findByCompanyIdAndStatus(Long companyId, ApplicationStatus status);
    List<Application> findByStatus(ApplicationStatus status);

    @Query("SELECT a FROM Application a JOIN FETCH a.company WHERE a.status = :status ORDER BY a.createdAt ASC")
    List<Application> findByStatusOrderByCreatedAtAsc(@Param("status") ApplicationStatus status);

    @Query("SELECT a FROM Application a JOIN FETCH a.company WHERE a.status IN (:statuses) ORDER BY a.updatedAt DESC")
    Page<Application> findByStatusInOrderByUpdatedAtDesc(@Param("statuses") Collection<ApplicationStatus> statuses, Pageable pageable);

    @Query("SELECT a FROM Application a JOIN FETCH a.company LEFT JOIN FETCH a.documents LEFT JOIN FETCH a.caseWorker WHERE a.id = :id")
    Optional<Application> findByIdWithDocuments(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Application a SET a.caseWorker = :worker WHERE a.id = :id AND a.caseWorker IS NULL")
    int assignIfUnassigned(@Param("id") Long id, @Param("worker") CaseWorker worker);
}
