package se.comerit.resurs.api.v1.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import se.comerit.resurs.api.v1.dto.ApplicationRequest;
import se.comerit.resurs.api.v1.mapper.ApplicationMapper;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.rating.ApplicationData;
import se.comerit.resurs.rating.ScoringResult;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.CompanyRepository;

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

    public Long submitApplication(
        String orgNumber,
        ApplicationRequest application) {
        Company company = getCompany(orgNumber).orElseThrow();

        ApplicationData data = ApplicationMapper.toApplicationData(application);
        ScoringResult score = scoringService.score(data);

        // TODO Audit log

        Application app = new Application(
            company, 
            application.requestedAmount(), 
            application.purpose(),
            null,
            null,
            score.summary(),
            null, // ScoringResult
            null // AuditLog
        );

        app = applicationRepository.save(app);

        return app.getId();
    }
}
