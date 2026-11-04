package se.comerit.resurs.api.v1.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import se.comerit.resurs.audit.WorkerAssigned;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.CaseWorkerRepository;
import se.comerit.resurs.security.CaseWorkerPrincipal;

/**
 * Central policy for assigning a case worker to an application. The current
 * strategy is "first interaction wins": the first case worker who opens an
 * application or makes a decision becomes its handler. The assignment is
 * persisted on the {@link Application} and never overwritten, so alternative
 * assignment mechanisms (queue claims, manual reassignment) can be layered on
 * later while keeping this class as the single point of control.
 */
@Service
public class CaseWorkerAssignmentService {

    private final ApplicationRepository applicationRepository;
    private final CaseWorkerRepository caseWorkerRepository;
    private final AuditLogService auditLogService;

    public CaseWorkerAssignmentService(ApplicationRepository applicationRepository,
            CaseWorkerRepository caseWorkerRepository, AuditLogService auditLogService) {
        this.applicationRepository = applicationRepository;
        this.caseWorkerRepository = caseWorkerRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Assigns {@code worker} to the application if no worker is assigned yet.
     * Runs in its own transaction so that it can be called from read-only
     * contexts (e.g. viewing application details) and still be persisted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureAssigned(Long applicationId, CaseWorkerPrincipal worker) {
        int assigned = applicationRepository.assignIfUnassigned(
                applicationId, caseWorkerRepository.getReferenceById(worker.id()));
        if (assigned > 0) {
            Application application = applicationRepository.findById(applicationId)
                    .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
            auditLogService.append(application, new WorkerAssigned(worker.id(), worker.name()));
        }
    }

    /**
     * Assigns {@code worker} to an already-loaded application if unassigned.
     * Intended for callers working in their own read-write transaction, where
     * the assignment is flushed together with the entity's other changes.
     */
    public void assignIfUnassigned(Application application, CaseWorkerPrincipal worker) {
        if (application.getCaseWorker() != null) {
            return;
        }
        application.assignTo(caseWorkerRepository.getReferenceById(worker.id()));
        auditLogService.append(application, new WorkerAssigned(worker.id(), worker.name()));
    }
}