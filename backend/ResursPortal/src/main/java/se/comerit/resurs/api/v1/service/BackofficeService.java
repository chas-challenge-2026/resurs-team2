package se.comerit.resurs.api.v1.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotBlank;
import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.dto.ApplicationOverview;
import se.comerit.resurs.api.v1.dto.ApplicationResponse;
import se.comerit.resurs.api.v1.dto.DecisionRequest;
import se.comerit.resurs.api.v1.mapper.ApplicationMapper;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.exception.ApplicationAlreadyDecidedException;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.repository.ApplicationRepository;

@Service
public class BackofficeService {

    private final ApplicationRepository repository;
    private final AuditLogService auditLogService;

    public BackofficeService(ApplicationRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public @Nonnull ApplicationResponse decide(@Nonnull DecisionRequest request, @Nonnull String caseWorker) {
        Application application = repository.findById(request.applicationId())
                .orElseThrow(() -> new ApplicationNotFoundException(request.applicationId()));

        if (application.getStatus() == ApplicationStatus.APPROVED
                || application.getStatus() == ApplicationStatus.REJECTED) {
            throw new ApplicationAlreadyDecidedException(request.applicationId());
        }

        ApplicationStatus status = switch (request.decision()) {
            case APPROVED -> ApplicationStatus.APPROVED;
            case REJECTED -> ApplicationStatus.REJECTED;
        };
        application.setStatus(status);
        // TODO: What is this point of this?
        application.setDecision(request.decision());
        application.setDecisionReason(request.comment());

        Map<String, String> auditDetails = new LinkedHashMap<>();
        auditDetails.put("decision", request.decision().name());
        auditDetails.put("worker", caseWorker);
        if (request.comment() != null && !request.comment().isBlank()) {
            auditDetails.put("comment", request.comment());
        }
        application.setAuditLog(auditLogService.append(application, "MANUAL_DECISION", auditDetails));

        // TODO: send email notification to the company about the decision.

        return ApplicationMapper.toResponse(repository.save(application));
    }

    @Transactional(readOnly = true)
    public @Nonnull ApplicationOverview applicationOverview(String caseWorker) {
        List<Application> reviewApplications = repository
                .findByStatusOrderByCreatedAtAsc(ApplicationStatus.UNDER_REVIEW);
        List<Application> decidedApplications = repository.findByStatusInOrderByUpdatedAtDesc(
                List.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED),
                PageRequest.of(0, 20));

        return ApplicationMapper.toApplicationOverview(reviewApplications, decidedApplications, caseWorker);
    }

    @Transactional(readOnly = true)
    public @Nonnull ApplicationDetailsResponse viewApplicationDetails(@Nonnull Long id, @NotBlank String caseWorker) {
        return repository.findByIdWithDocuments(id)
                .map(app -> ApplicationMapper.toDetailsResponse(app, caseWorker))
                .orElseThrow(() -> new ApplicationNotFoundException(id));
    }
}
