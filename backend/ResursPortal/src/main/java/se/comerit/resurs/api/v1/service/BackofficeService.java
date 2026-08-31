package se.comerit.resurs.api.v1.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotBlank;
import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.dto.ApplicationOverview;
import se.comerit.resurs.api.v1.mapper.ApplicationMapper;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.repository.ApplicationRepository;

@Service
public class BackofficeService {

    private final ApplicationRepository repository;

    public BackofficeService(ApplicationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public @Nonnull ApplicationOverview applicationOverview(String caseWorker) {
        List<Application> reviewApplications = repository
                .findByStatusOrderByCreatedAtAsc(ApplicationStatus.UNDER_REVIEW);
        List<Application> decidedApplications = repository.findTop20ByStatusInOrderByUpdatedAtDesc(
                List.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED));

        return ApplicationMapper.toApplicationOverview(reviewApplications, decidedApplications, caseWorker);
    }

    @Transactional(readOnly = true)
    public @Nonnull ApplicationDetailsResponse viewApplicationDetails(@Nonnull Long id, @NotBlank String caseWorker) {
        return repository.findByIdWithDocuments(id)
                .map(app -> ApplicationMapper.toDetailsResponse(app, caseWorker))
                .orElseThrow(() -> new ApplicationNotFoundException(id));
    }
}
