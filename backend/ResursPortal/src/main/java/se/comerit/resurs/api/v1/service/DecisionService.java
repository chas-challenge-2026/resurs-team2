package se.comerit.resurs.api.v1.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nonnull;
import se.comerit.resurs.api.v1.dto.DecisionRequest;
import se.comerit.resurs.api.v1.dto.ApplicationResponse;
import se.comerit.resurs.api.v1.mapper.ApplicationMapper;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.exception.ApplicationAlreadyDecidedException;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.repository.ApplicationRepository;

@Service
public class DecisionService {

    private final ApplicationRepository repository;
    private final AuditLogService auditLogService;

    public DecisionService(ApplicationRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public @Nonnull ApplicationResponse decide(@Nonnull Long applicationId,
            @Nonnull DecisionRequest request, @Nonnull String caseWorker) {
        Application application = repository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (application.getStatus() == ApplicationStatus.APPROVED
                || application.getStatus() == ApplicationStatus.REJECTED) {
            throw new ApplicationAlreadyDecidedException(applicationId);
        }

        application.setStatus(ApplicationMapper.toStatus(request.decision()));
        application.setDecision(request.decision());
        application.setDecisionReason(request.comment());

        Map<String, String> auditDetails = new LinkedHashMap<>();
        auditDetails.put("decision", request.decision().name());
        auditDetails.put("worker", caseWorker);
        if (request.comment() != null && !request.comment().isBlank()) {
            auditDetails.put("comment", request.comment());
        }
        auditLogService.append(application, "MANUAL_DECISION", auditDetails);

        return ApplicationMapper.toResponse(repository.save(application));
    }
}