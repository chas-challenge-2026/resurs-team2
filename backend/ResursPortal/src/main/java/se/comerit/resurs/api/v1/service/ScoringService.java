package se.comerit.resurs.api.v1.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import se.comerit.resurs.api.v1.mapper.ApplicationMapper;
import se.comerit.resurs.audit.ScoringRun;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.rating.ApplicationData;
import se.comerit.resurs.rating.CheckResult;
import se.comerit.resurs.rating.CheckStatus;
import se.comerit.resurs.rating.DecisionEngine;
import se.comerit.resurs.rating.Score;
import se.comerit.resurs.rating.ScoringCheck;
import se.comerit.resurs.rating.ScoringResult;
import se.comerit.resurs.repository.ApplicationRepository;

/**
 * ScoringService – orchestrates every scoring check and synthesizes the final
 * decision via the {@link DecisionEngine}.
 *
 * <p>Every check is a Spring bean implementing {@link ScoringCheck} and is
 * auto-injected as a {@code List<ScoringCheck>}. Each check is a pure function
 * of the input {@link ApplicationData} and returns its {@link CheckResult}s.
 * All results feed the decision engine, while the legacy {@link Score}
 * projection keeps the controller interface stable.
 */
@Service
public class ScoringService {
    public static final int BASE_CREDIT_POINTS = 100;

    private final List<ScoringCheck> scoringChecks;
    private final DecisionEngine decisionEngine;
    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    public ScoringService(List<ScoringCheck> scoringChecks, DecisionEngine decisionEngine,
            ApplicationRepository applicationRepository, ObjectMapper objectMapper,
            AuditLogService auditLogService, EmailService emailService) {
        this.scoringChecks = scoringChecks;
        this.decisionEngine = decisionEngine;
        this.applicationRepository = applicationRepository;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
    }

    public ScoringResult score(ApplicationData input) {
        List<CheckResult> checks = new ArrayList<>();
        for (ScoringCheck check : scoringChecks) {
            checks.addAll(check.evaluate(input));
        }

        return decisionEngine.decide(checks);
    }

    /**
     * Scores an application by id: reads the persisted financial data, runs
     * the scoring engine, persists the outcome, and notifies the applicant.
     * Silently returns if the application or its financial data is missing.
     */
    public void scoreApplication(Long applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElse(null);
        if (app == null) {
            return;
        }

        String financialDataJson = app.getFinancialData();
        if (financialDataJson == null) {
            return;
        }

        ApplicationData data;
        try {
            data = objectMapper.readValue(financialDataJson, ApplicationData.class);
        } catch (Exception _) {
            return;
        }

        ScoringResult result = score(data);
        Score scoring = toScore(result);

        app.setStatus(ApplicationMapper.toStatus(result));
        app.setDecision(ApplicationMapper.toDecision(result));
        app.setDecisionReason(result.summary());
        app.setScoringResult(scoring.scoringLog());

        auditLogService.append(app, new ScoringRun(scoring.decision(), String.valueOf(scoring.flagCount())));

        applicationRepository.save(app);

        if (app.getDecision() != null) {
            emailService.sendDecision(app);
        } else {
            emailService.sendStatusUpdate(app);
        }
    }

    public static Score toScore(ScoringResult result) {
        var checks = result.checks();
        int flagCount = (int) checks.stream()
                .filter(check -> check.status() == CheckStatus.FLAG)
                .count();
        @SuppressWarnings("null")
        int creditPoints = BASE_CREDIT_POINTS + checks.stream()
                .mapToInt(CheckResult::scoreDelta)
                .sum();
        String scoringLog = checks.stream()
                .map(check -> check.ruleName() + "=" + check.status())
                .collect(Collectors.joining(", "))
                + ", kreditPoäng=" + creditPoints + " (ANVÄNDS EJ I BESLUT)";

        return new Score(
                result.decision().decisionValue(),
                flagCount,
                scoringLog,
                result.decision().statusValue(),
                result.summary());
    }
}