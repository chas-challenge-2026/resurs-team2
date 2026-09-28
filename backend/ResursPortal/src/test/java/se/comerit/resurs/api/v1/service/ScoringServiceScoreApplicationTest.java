package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.ObjectMapper;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.entity.Decision;
import se.comerit.resurs.rating.ApplicationData;
import se.comerit.resurs.rating.CheckResult;
import se.comerit.resurs.rating.CheckStatus;
import se.comerit.resurs.rating.DecisionEngine;
import se.comerit.resurs.rating.ScoringResult;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;

/**
 * Unit tests for {@link ScoringService#scoreApplication}, covering the
 * post-scoring bookkeeping: field updates, SCORING_RUN audit entry, save, and
 * the decision-vs-status email selection.
 */
class ScoringServiceScoreApplicationTest {

    private ApplicationRepository repository;
    private AuditLogRepository auditLogRepository;
    private ObjectMapper objectMapper;
    private AuditLogService auditLogService;
    private EmailService emailService;
    private DecisionEngine decisionEngine;
    private ScoringService scoringService;

    private Company company;
    private Application app;

    @BeforeEach
    void setUp() throws Exception {
        repository = mock(ApplicationRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        objectMapper = new ObjectMapper();
        auditLogService = new AuditLogService(auditLogRepository, repository, objectMapper);
        emailService = mock(EmailService.class);
        decisionEngine = mock(DecisionEngine.class);
        scoringService = new ScoringService(List.of(), decisionEngine, repository, objectMapper, auditLogService, emailService);

        company = new Company("556677-8899", "Testbolaget AB", "Kalle Kula");
        app = new Application(company, new BigDecimal("300000"), "Rörelsekapital");
        setApplicationId(app, 7L);
        app.setFinancialData(objectMapper.writeValueAsString(
                new ApplicationData(500_000.0, 1_000_000.0, 400_000.0, 200_000.0, 500_000.0,
                        150_000.0, 1_000_000.0, new BigDecimal("300000"),
                        120_000.0, -50_000.0, 20_000.0, "IT")));
        when(repository.findById(7L)).thenReturn(Optional.of(app));
    }

    private static void setApplicationId(Application application, long id) {
        try {
            var field = Application.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(application, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to assign application id", e);
        }
    }

    private ScoringResult approvedResult() {
        return new ScoringResult(
                se.comerit.resurs.rating.Decision.APPROVED,
                List.of(new CheckResult("solidity", 0.5, 0.2, CheckStatus.OK, 0, "ok")),
                "ANSÖKAN GODKÄND");
    }

    private ScoringResult reviewResult() {
        return new ScoringResult(
                se.comerit.resurs.rating.Decision.UNDER_REVIEW,
                List.of(new CheckResult("solidity", 0.5, 0.2, CheckStatus.OK, 0, "ok")),
                "MANUELL GRANSKNING");
    }

    @Test
    @DisplayName("Approved decision updates the application, logs SCORING_RUN, saves and sends a decision email")
    void approvedDecisionPersistsAndSendsDecisionEmail() {
        when(decisionEngine.decide(any())).thenReturn(approvedResult());

        scoringService.scoreApplication(7L);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(app.getDecision()).isEqualTo(Decision.APPROVED);
        assertThat(app.getDecisionReason()).isEqualTo("ANSÖKAN GODKÄND");
        assertThat(app.getScoringResult()).isNotBlank();

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEntry())
                .contains("\"action\":\"SCORING_RUN\"")
                .contains("\"result\":\"APPROVED\"")
                .contains("\"flags\"");

        verify(repository).save(app);
        verify(emailService).sendDecision(app);
        verify(emailService, never()).sendStatusUpdate(any(Application.class));
    }

    @Test
    @DisplayName("Manual-review outcome persists UNDER_REVIEW and sends a status update email")
    void reviewOutcomeSendsStatusUpdateEmail() {
        when(decisionEngine.decide(any())).thenReturn(reviewResult());

        scoringService.scoreApplication(7L);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(app.getDecision()).isNull();
        verify(emailService).sendStatusUpdate(app);
        verify(emailService, never()).sendDecision(any(Application.class));
    }

    @Test
    @DisplayName("Does nothing when the application does not exist")
    void missingApplicationIsIgnored() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        scoringService.scoreApplication(99L);

        verify(repository, never()).save(any());
        verify(emailService, never()).sendDecision(any(Application.class));
        verify(emailService, never()).sendStatusUpdate(any(Application.class));
    }

    @Test
    @DisplayName("Does nothing when the application has no financial data")
    void missingFinancialDataIsIgnored() {
        app.setFinancialData(null);

        scoringService.scoreApplication(7L);

        verify(repository, never()).save(any());
        verify(emailService, never()).sendDecision(any(Application.class));
        verify(emailService, never()).sendStatusUpdate(any(Application.class));
    }
}