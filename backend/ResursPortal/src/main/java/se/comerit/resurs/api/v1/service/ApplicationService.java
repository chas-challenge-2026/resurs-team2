package se.comerit.resurs.api.v1.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import se.comerit.resurs.api.v1.dto.ApplicationRequest;
import se.comerit.resurs.api.v1.mapper.ApplicationMapper;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.exception.CompanyNotFoundException;
import se.comerit.resurs.rating.ApplicationData;
import se.comerit.resurs.rating.Score;
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
            null,
            null,
            score.summary(),
            null,
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
}
