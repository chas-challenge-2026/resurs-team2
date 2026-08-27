package se.comerit.resurs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByCompanyId(Long companyId);
    List<Application> findByStatus(ApplicationStatus status);
    List<Application> findByStatusOrderByCreatedAtAsc(ApplicationStatus status);
    List<Application> findTop20ByStatusInOrderByUpdatedAtDesc(Collection<ApplicationStatus> statuses);

    @Query("SELECT a FROM Application a LEFT JOIN FETCH a.documents WHERE a.id = :id")
    Optional<Application> findByIdWithDocuments(@Param("id") Long id);
}
