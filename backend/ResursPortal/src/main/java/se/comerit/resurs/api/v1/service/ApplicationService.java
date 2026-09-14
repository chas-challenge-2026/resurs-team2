package se.comerit.resurs.api.v1.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.Nonnull;
import se.comerit.resurs.audit.ApplicationCreated;
import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.dto.ApplicationRequest;
import se.comerit.resurs.api.v1.dto.ApplicationResponse;
import se.comerit.resurs.api.v1.mapper.ApplicationMapper;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.exception.CompanyNotFoundException;
import se.comerit.resurs.rating.ApplicationData;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.CompanyRepository;
import se.comerit.resurs.security.CaseWorkerPrincipal;
import se.comerit.resurs.security.UserPrincipal;

@Service
public class ApplicationService {
    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final ScoringService scoringService;
    private final AuditLogService auditLogService;
    private final CaseWorkerAssignmentService caseWorkerAssignmentService;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public ApplicationService(CompanyRepository companyRepository, ApplicationRepository applicationRepository,
            ScoringService scoringService, AuditLogService auditLogService,
            CaseWorkerAssignmentService caseWorkerAssignmentService, ObjectMapper objectMapper,
            EmailService emailService) {
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.scoringService = scoringService;
        this.auditLogService = auditLogService;
        this.caseWorkerAssignmentService = caseWorkerAssignmentService;
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    public Optional<Company> getCompany(String orgNumber) {
        return companyRepository.findByOrgNumber(orgNumber);
    }

    @Transactional
    public Long submitApplication(
            String orgNumber,
            ApplicationRequest application) {
        Company company = getCompany(orgNumber)
                .orElseThrow(() -> new CompanyNotFoundException(orgNumber));

        ApplicationData data = ApplicationMapper.toApplicationData(application);

        String financialDataJson;
        try {
            financialDataJson = objectMapper.writeValueAsString(data);
        } catch (Exception _) {
            financialDataJson = null;
        }

        // TODO: Consider what should be the default status
        Application app = new Application(
                company,
                application.requestedAmount(),
                application.purpose());
        app.setFinancialData(financialDataJson);

        app = applicationRepository.save(app);

        emailService.sendApplicationSubmitted(app);

        auditLogService.append(app, new ApplicationCreated(orgNumber));

        scoringService.scoreApplication(app.getId());

        return app.getId();
    }

    /**
     * Returns the applications visible to the caller. A case worker sees all
     * applications; a company sees only its own. When {@code status} is
     * supplied the result is narrowed to that status, otherwise case workers
     * default to {@link ApplicationStatus#UNDER_REVIEW} and companies see
     * everything.
     */
    @Transactional(readOnly = true)
    public @Nonnull List<ApplicationResponse> listApplications(UserPrincipal principal,
            ApplicationStatus status) {
        if (principal instanceof CaseWorkerPrincipal) {
            ApplicationStatus effective = status != null ? status : ApplicationStatus.UNDER_REVIEW;
            return applicationRepository.findByStatus(effective).stream()
                    .map(ApplicationMapper::toResponse).toList();
        }

        String orgNumber = principal.asCompany().orgNumber();
        Company company = getCompany(orgNumber)
                .orElseThrow(() -> new CompanyNotFoundException(orgNumber));
        if (status != null) {
            return applicationRepository.findByCompanyIdAndStatus(company.getId(), status).stream()
                    .map(ApplicationMapper::toResponse).toList();
        }
        return applicationRepository.findByCompanyId(company.getId()).stream()
                .map(ApplicationMapper::toResponse).toList();
    }

    /**
     * Returns the details of a single application. A case worker may view any
     * application; a company may only view its own (mirrors the legacy
     * controller). For anything the caller is not allowed to see, or that does
     * not exist, an {@link ApplicationNotFoundException} is thrown so that the
     * existence of other applications is not leaked.
     */
    @Transactional(readOnly = true)
    public @Nonnull ApplicationDetailsResponse viewApplication(Long id, UserPrincipal principal) {
        if (principal instanceof CaseWorkerPrincipal caseWorker) {
            caseWorkerAssignmentService.ensureAssigned(id, caseWorker);
            Application app = applicationRepository.findByIdWithDocuments(id)
                    .orElseThrow(() -> new ApplicationNotFoundException(id));
            return ApplicationMapper.toDetailsResponse(app);
        }

        Application app = applicationRepository.findByIdWithDocuments(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        String orgNumber = principal.asCompany().orgNumber();
        if (!app.getCompany().getOrgNumber().equals(orgNumber)) {
            throw new ApplicationNotFoundException(id);
        }
        return ApplicationMapper.toDetailsResponse(app);
    }
}
