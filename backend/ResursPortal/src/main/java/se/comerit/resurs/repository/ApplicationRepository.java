package se.comerit.resurs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByCompanyId(Long companyId);
    List<Application> findByStatus(ApplicationStatus status);

    @Query("SELECT a FROM Application a JOIN FETCH a.company WHERE a.status = :status ORDER BY a.createdAt ASC")
    List<Application> findByStatusOrderByCreatedAtAsc(@Param("status") ApplicationStatus status);

    @Query("SELECT a FROM Application a JOIN FETCH a.company WHERE a.status IN (:statuses) ORDER BY a.updatedAt DESC")
    List<Application> findByStatusInOrderByUpdatedAtDesc(@Param("statuses") Collection<ApplicationStatus> statuses, Pageable pageable);

    @Query("SELECT a FROM Application a JOIN FETCH a.company LEFT JOIN FETCH a.documents WHERE a.id = :id")
    Optional<Application> findByIdWithDocuments(@Param("id") Long id);
}
