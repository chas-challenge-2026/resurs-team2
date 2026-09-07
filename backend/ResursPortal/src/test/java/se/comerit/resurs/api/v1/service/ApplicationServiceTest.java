package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;



import se.comerit.resurs.api.v1.dto.ApplicationRequest;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.exception.CompanyNotFoundException;
import se.comerit.resurs.rating.CheckResult;
import se.comerit.resurs.rating.CheckStatus;
import se.comerit.resurs.rating.Decision;
import se.comerit.resurs.rating.Score;
import se.comerit.resurs.rating.ScoringResult;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.CompanyRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link ApplicationService#submitApplication}.
 *
 * <p>Repositories and the {@link ScoringService} are mocked, while a real
 * {@link AuditLogService} is used so the resulting audit log JSON can be
 * asserted for correctness (each entry must be present, in order, and carry the
 * right action and details).
 */
class ApplicationServiceTest {

    private CompanyRepository companyRepository;
    private ApplicationRepository applicationRepository;
    private ScoringService scoringService;
    private AuditLogService auditLogService;
    private ApplicationService applicationService;

    private Company company;
    private ApplicationRequest validRequest;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        scoringService = mock(ScoringService.class);
        auditLogService = new AuditLogService(new ObjectMapper());

        applicationService = new ApplicationService(
                companyRepository, applicationRepository, scoringService, auditLogService);

        company = new Company("556677-8899", "Testbolaget AB", "Kalle Kula");

        validRequest = new ApplicationRequest(
                500_000.0,
                1_000_000.0,
                400_000.0,
                200_000.0,
                500_000.0,
                150_000.0,
                1_000_000.0,
                new BigDecimal("300000"),
                "Rörelsekapital",
                120_000.0,
                -50_000.0,
                20_000.0,
                "IT");
    }

    private ScoringResult approvedScoringResult() {
        return new ScoringResult(
                Decision.APPROVED,
                List.of(new CheckResult("solidity", 0.5, 0.2, CheckStatus.OK, 0, "ok")),
                "ANSÖKAN GODKÄND");
    }

    private void stubSaveReturnsSavedWithId(long id) {
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(invocation -> {
                    Application app = invocation.getArgument(0);
                    setApplicationId(app, id);
                    return app;
                });
    }

    // Application#id has no public setter; clear the id field reflectively to
    // emulate the repository assigning a generated id on save.
    private static void setApplicationId(Application app, long id) {
        try {
            var field = Application.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(app, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to assign application id", e);
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Scores the application and persists it, returning the id")
        void scoresAndPersistsApplication() {
            when(companyRepository.findByOrgNumber("556677-8899"))
                    .thenReturn(Optional.of(company));
            ScoringResult result = approvedScoringResult();
            when(scoringService.score(any())).thenReturn(result);
            stubSaveReturnsSavedWithId(42L);

            try (MockedStatic<ScoringService> staticMock = Mockito.mockStatic(ScoringService.class)) {
                staticMock.when(() -> ScoringService.toScore(result))
                        .thenReturn(new Score("APPROVED", 0, "log", "APPROVED", "ANSÖKAN GODKÄND"));

                Long id = applicationService.submitApplication("556677-8899", validRequest);

                assertThat(id).isEqualTo(42L);
            }

            verify(scoringService).score(any());
            verify(applicationRepository).save(any(Application.class));
        }

        @Test
        @DisplayName("Persists the supplied company, requested amount and purpose")
        void persistsCorrectFields() {
            when(companyRepository.findByOrgNumber("556677-8899"))
                    .thenReturn(Optional.of(company));
            when(scoringService.score(any())).thenReturn(approvedScoringResult());
            stubSaveReturnsSavedWithId(1L);

            try (MockedStatic<ScoringService> staticMock = Mockito.mockStatic(ScoringService.class)) {
                staticMock.when(() -> ScoringService.toScore(any()))
                        .thenReturn(new Score("APPROVED", 0, "log", "APPROVED", "ANSÖKAN GODKÄND"));

                applicationService.submitApplication("556677-8899", validRequest);
            }

            verify(applicationRepository).save(argThat(app ->
                    app.getCompany().equals(company)
                    && app.getRequestedAmount().compareTo(new BigDecimal("300000")) == 0
                    && "Rörelsekapital".equals(app.getPurpose())));
        }
    }

    @Nested
    @DisplayName("Audit log")
    class AuditLog {

        @Test
        @DisplayName("Creates an APPLICATION_CREATED entry carrying the org number")
        void applicationCreatedEntryPresent() {
            when(companyRepository.findByOrgNumber("556677-8899"))
                    .thenReturn(Optional.of(company));
            when(scoringService.score(any())).thenReturn(approvedScoringResult());
            stubSaveReturnsSavedWithId(1L);

            try (MockedStatic<ScoringService> staticMock = Mockito.mockStatic(ScoringService.class)) {
                staticMock.when(() -> ScoringService.toScore(any()))
                        .thenReturn(new Score("APPROVED", 0, "log", "APPROVED", "ANSÖKAN GODKÄND"));

                applicationService.submitApplication("556677-8899", validRequest);
            }

            verify(applicationRepository).save(argThat(app -> {
                String log = app.getAuditLog();
                return log.contains("\"action\":\"APPLICATION_CREATED\"")
                        && log.contains("\"orgNumber\":\"556677-8899\"")
                        && log.contains("\"ts\":");
            }));
        }

        @Test
        @DisplayName("Creates a SCORING_RUN entry carrying the decision and flag count")
        void scoringRunEntryPresent() {
            when(companyRepository.findByOrgNumber("556677-8899"))
                    .thenReturn(Optional.of(company));
            when(scoringService.score(any())).thenReturn(approvedScoringResult());
            stubSaveReturnsSavedWithId(1L);

            try (MockedStatic<ScoringService> staticMock = Mockito.mockStatic(ScoringService.class)) {
                staticMock.when(() -> ScoringService.toScore(any()))
                        .thenReturn(new Score("APPROVED", 2, "log", "APPROVED", "ANSÖKAN GODKÄND"));

                applicationService.submitApplication("556677-8899", validRequest);
            }

            verify(applicationRepository).save(argThat(app -> {
                String log = app.getAuditLog();
                return log.contains("\"action\":\"SCORING_RUN\"")
                        && log.contains("\"result\":\"APPROVED\"")
                        && log.contains("\"flags\":\"2\"");
            }));
        }

        @Test
        @DisplayName("APPLICATION_CREATED precedes SCORING_RUN, both as a single JSON array")
        void entriesInOrderAsSingleArray() {
            when(companyRepository.findByOrgNumber("556677-8899"))
                    .thenReturn(Optional.of(company));
            when(scoringService.score(any())).thenReturn(approvedScoringResult());
            stubSaveReturnsSavedWithId(1L);

            try (MockedStatic<ScoringService> staticMock = Mockito.mockStatic(ScoringService.class)) {
                staticMock.when(() -> ScoringService.toScore(any()))
                        .thenReturn(new Score("APPROVED", 0, "log", "APPROVED", "ANSÖKAN GODKÄND"));

                applicationService.submitApplication("556677-8899", validRequest);
            }

            verify(applicationRepository).save(argThat(app -> {
                String log = app.getAuditLog().trim();
                int created = log.indexOf("APPLICATION_CREATED");
                int scoring = log.indexOf("SCORING_RUN");
                return log.startsWith("[")
                        && log.endsWith("]")
                        && created >= 0
                        && scoring > created;
            }));
        }
    }

    @Nested
    @DisplayName("Failure cases")
    class FailureCases {

        @Test
        @DisplayName("Does not save anything when the company is unknown")
        void companyNotFoundNotSaved() {
            when(companyRepository.findByOrgNumber("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    applicationService.submitApplication("unknown", validRequest))
                    .isInstanceOf(CompanyNotFoundException.class)
                    .hasMessageContaining("unknown");

            verify(applicationRepository, never()).save(any(Application.class));
            verify(scoringService, never()).score(any());
        }
    }

    @Nested
    @DisplayName("Scoring result")
    class ScoringResultCheck {

        @Test
        @DisplayName("Summary lands in decision_reason, scoring log in scoring_result, and status/decision are set")
        void scoringFieldsAreSetCorrectly() {
            when(companyRepository.findByOrgNumber("556677-8899"))
                    .thenReturn(Optional.of(company));
            ScoringResult result = approvedScoringResult();
            when(scoringService.score(any())).thenReturn(result);
            stubSaveReturnsSavedWithId(1L);

            try (MockedStatic<ScoringService> staticMock = Mockito.mockStatic(ScoringService.class)) {
                staticMock.when(() -> ScoringService.toScore(result))
                        .thenReturn(new Score("APPROVED", 0, "solidity=OK, kreditPoäng=100 (ANVÄNDS EJ I BESLUT)", "APPROVED", "ANSÖKAN GODKÄND"));

                applicationService.submitApplication("556677-8899", validRequest);
            }

            verify(applicationRepository).save(argThat(app ->
                    "ANSÖKAN GODKÄND".equals(app.getDecisionReason())
                    && "solidity=OK, kreditPoäng=100 (ANVÄNDS EJ I BESLUT)".equals(app.getScoringResult())
                    && app.getStatus() == ApplicationStatus.APPROVED
                    && app.getDecision() == se.comerit.resurs.entity.Decision.APPROVED));
        }

        @Test
        @DisplayName("REVIEW decision maps to null decision and UNDER_REVIEW status")
        void reviewDecisionSetsNullDecisionAndUnderReviewStatus() {
            when(companyRepository.findByOrgNumber("556677-8899"))
                    .thenReturn(Optional.of(company));
            ScoringResult result = new ScoringResult(
                    se.comerit.resurs.rating.Decision.UNDER_REVIEW,
                    List.of(new CheckResult("solidity", 0.5, 0.2, CheckStatus.OK, 0, "ok")),
                    "MANUELL GRANSKNING");
            when(scoringService.score(any())).thenReturn(result);
            stubSaveReturnsSavedWithId(1L);

            try (MockedStatic<ScoringService> staticMock = Mockito.mockStatic(ScoringService.class)) {
                staticMock.when(() -> ScoringService.toScore(result))
                        .thenReturn(new Score("REVIEW", 0, "solidity=OK, kreditPoäng=100 (ANVÄNDS EJ I BESLUT)", "UNDER_REVIEW", "MANUELL GRANSKNING"));

                applicationService.submitApplication("556677-8899", validRequest);
            }

            verify(applicationRepository).save(argThat(app ->
                    "MANUELL GRANSKNING".equals(app.getDecisionReason())
                    && app.getDecision() == null
                    && app.getStatus() == ApplicationStatus.UNDER_REVIEW));
        }
    }
}
