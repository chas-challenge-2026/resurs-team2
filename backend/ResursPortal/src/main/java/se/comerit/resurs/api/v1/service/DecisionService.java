package se.comerit.resurs.api.v1.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nonnull;
import se.comerit.resurs.audit.ManualDecision;
import se.comerit.resurs.api.v1.dto.DecisionRequest;
import se.comerit.resurs.api.v1.dto.ApplicationResponse;
import se.comerit.resurs.api.v1.mapper.ApplicationMapper;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.exception.ApplicationAlreadyDecidedException;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.security.CaseWorkerPrincipal;

@Service
public class DecisionService {

    private final ApplicationRepository repository;
    private final AuditLogService auditLogService;
    private final CaseWorkerAssignmentService caseWorkerAssignmentService;
    private final EmailService emailService;

    public DecisionService(ApplicationRepository repository, AuditLogService auditLogService,
            CaseWorkerAssignmentService caseWorkerAssignmentService, EmailService emailService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.caseWorkerAssignmentService = caseWorkerAssignmentService;
        this.emailService = emailService;
    }

    @Transactional
    public @Nonnull ApplicationResponse decide(@Nonnull Long applicationId,
            @Nonnull DecisionRequest request, @Nonnull CaseWorkerPrincipal caseWorker) {
        Application application = repository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (application.getStatus() == ApplicationStatus.APPROVED
                || application.getStatus() == ApplicationStatus.REJECTED) {
            throw new ApplicationAlreadyDecidedException(applicationId);
        }

        caseWorkerAssignmentService.assignIfUnassigned(application, caseWorker);

        application.setStatus(ApplicationMapper.toStatus(request.decision()));
        application.setDecision(request.decision());
        application.setDecisionReason(request.comment());

        auditLogService.append(application,
                new ManualDecision(request.decision().name(), caseWorker.name(), blankToNull(request.comment())));

        Application saved = repository.save(application);

        emailService.sendDecision(
            saved.getCompany().getAuthorizedSignatory(),
            saved.getId(),
            request.decision().name(),
            request.comment()
        );

        return ApplicationMapper.toResponse(saved);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}