package se.comerit.resurs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByCompanyId(Long companyId);
    List<Application> findByStatus(ApplicationStatus status);
    List<Application> findByStatusOrderByCreatedAtAsc(ApplicationStatus status);
    List<Application> findTop20ByStatusInOrderByUpdatedAtDesc(Collection<ApplicationStatus> statuses);

    @EntityGraph(attributePaths = {"company", "documents"})
    Optional<Application> findByIdWithDocuments(Long id);
}
