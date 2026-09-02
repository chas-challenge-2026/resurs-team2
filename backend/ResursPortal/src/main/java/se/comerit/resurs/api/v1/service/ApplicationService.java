package se.comerit.resurs.api.v1.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nonnull;
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
import se.comerit.resurs.rating.Score;
import se.comerit.resurs.rating.ScoringResult;
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

    public ApplicationService(CompanyRepository companyRepository, ApplicationRepository applicationRepository,
            ScoringService scoringService, AuditLogService auditLogService) {
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.scoringService = scoringService;
        this.auditLogService = auditLogService;
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
        ScoringResult score = scoringService.score(data);
        Score scoring = ScoringService.toScore(score);

        Application app = new Application(
            company, 
            application.requestedAmount(), 
            application.purpose(),
            ApplicationMapper.toStatus(score),
            ApplicationMapper.toDecision(score),
            score.summary(),
            scoring.scoringLog(),
            null
        );

        Map<String, String> createdDetails = new LinkedHashMap<>();
        createdDetails.put("orgNumber", orgNumber);
        auditLogService.append(app, "APPLICATION_CREATED", createdDetails);

        Map<String, String> scoringDetails = new LinkedHashMap<>();
        scoringDetails.put("result", scoring.decision());
        scoringDetails.put("flags", String.valueOf(scoring.flagCount()));
        auditLogService.append(app, "SCORING_RUN", scoringDetails);

        app = applicationRepository.save(app);

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
        Application app = applicationRepository.findByIdWithDocuments(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        if (principal instanceof CaseWorkerPrincipal caseWorker) {
            return ApplicationMapper.toDetailsResponse(app, caseWorker.name());
        }

        String orgNumber = principal.asCompany().orgNumber();
        if (!app.getCompany().getOrgNumber().equals(orgNumber)) {
            throw new ApplicationNotFoundException(id);
        }
        return ApplicationMapper.toDetailsResponse(app, app.getCompany().getName());
    }
}
