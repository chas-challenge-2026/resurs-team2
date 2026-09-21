package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.ObjectMapper;

import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.dto.ApplicationRequest;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.exception.CompanyNotFoundException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;
import se.comerit.resurs.repository.CompanyRepository;
import se.comerit.resurs.security.CaseWorkerPrincipal;
import se.comerit.resurs.security.CompanyPrincipal;

/**
 * Unit tests for {@link ApplicationService#submitApplication} and
 * {@link ApplicationService#viewApplication}.
 *
 * <p>Repositories and the {@link ScoringService} are mocked; the submission
 * flow only persists the application with its serialized financial data, sends
 * the "received" notification, and delegates the scoring to
 * {@link ScoringService#scoreApplication}. A real {@link AuditLogService} is
 * used so the APPLICATION_CREATED audit entry can be asserted for correctness.
 */
class ApplicationServiceTest {

    private CompanyRepository companyRepository;
    private ApplicationRepository applicationRepository;
    private AuditLogRepository auditLogRepository;
    private ScoringService scoringService;
    private AuditLogService auditLogService;
    private CaseWorkerAssignmentService caseWorkerAssignmentService;
    private EmailService emailService;
    private ApplicationService applicationService;
    private ObjectMapper objectMapper;

    private Company company;
    private ApplicationRequest validRequest;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        scoringService = mock(ScoringService.class);
        objectMapper = new ObjectMapper();
        auditLogService = new AuditLogService(auditLogRepository, mock(ApplicationRepository.class),
                objectMapper);

        caseWorkerAssignmentService = mock(CaseWorkerAssignmentService.class);

        emailService = mock(EmailService.class);

        applicationService = new ApplicationService(
                companyRepository, applicationRepository, scoringService, auditLogService,
                caseWorkerAssignmentService, objectMapper,
                emailService, null);
        try {
            var field = ApplicationService.class.getDeclaredField("self");
            field.setAccessible(true);
            field.set(applicationService, applicationService);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set self reference", e);
        }

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

    // Application#documents is populated by JPA; make it an empty list so the
    // mapper can be exercised on a plain unit-test instance.
    private static void setEmptyDocuments(Application app) {
        try {
            var field = Application.class.getDeclaredField("documents");
            field.setAccessible(true);
            field.set(app, List.of());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to assign documents", e);
        }
    }

    private Application applicationWithFinancialData(Company owner, String financialData) {
        Application app = new Application(
                owner,
                new BigDecimal("300000"),
                "Rörelsekapital",
                ApplicationStatus.UNDER_REVIEW,
                null,
                null,
                null,
                financialData);
        setEmptyDocuments(app);
        return app;
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Submits the application, returns its id and delegates scoring")
        void submitsAndDelegatesScoring() {
            when(companyRepository.findByOrgNumber("556677-8899"))
                    .thenReturn(Optional.of(company));
            stubSaveReturnsSavedWithId(42L);

            Long id = applicationService.submitApplication("556677-8899", validRequest);

            assertThat(id).isEqualTo(42L);
            verify(scoringService).scoreApplication(42L);
            verify(applicationRepository).save(any(Application.class));
        }

        @Test
        @DisplayName("Persists the supplied company, requested amount, purpose and financial data")
        void persistsCorrectFields() {
            when(companyRepository.findByOrgNumber("556677-8899"))
                    .thenReturn(Optional.of(company));
            stubSaveReturnsSavedWithId(1L);

            applicationService.submitApplication("556677-8899", validRequest);

            verify(applicationRepository).save(argThat(app ->
                    app.getCompany().equals(company)
                    && app.getRequestedAmount().compareTo(new BigDecimal("300000")) == 0
                    && "Rörelsekapital".equals(app.getPurpose())
                    && app.getFinancialData() != null
                    && app.getFinancialData().contains("\"industry\":\"IT\"")));
        }

        @Test
        @DisplayName("Sends an application-received email to the authorized signatory")
        void sendsReceivedEmail() {
            when(companyRepository.findByOrgNumber("556677-8899"))
                    .thenReturn(Optional.of(company));
            stubSaveReturnsSavedWithId(1L);

            applicationService.submitApplication("556677-8899", validRequest);

            verify(emailService).sendApplicationSubmitted(argThat(app ->
                    app.getId().equals(1L)
                            && app.getCompany().getAuthorizedSignatory().equals("Kalle Kula")));
        }
    }

    @Nested
    @DisplayName("Audit log")
    class AuditLogEntries {

        @Test
        @DisplayName("Creates an APPLICATION_CREATED entry carrying the org number")
        void applicationCreatedEntryPresent() {
            when(companyRepository.findByOrgNumber("556677-8899"))
                    .thenReturn(Optional.of(company));
            stubSaveReturnsSavedWithId(1L);

            applicationService.submitApplication("556677-8899", validRequest);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getEntry())
                    .contains("\"action\":\"APPLICATION_CREATED\"")
                    .contains("\"orgNumber\":\"556677-8899\"");
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
            verify(scoringService, never()).scoreApplication(any());
            verify(emailService, never()).sendApplicationSubmitted(any(Application.class));
        }
    }

    @Nested
    @DisplayName("View application financial data")
    class ViewApplicationFinancialData {

        @Test
        @DisplayName("Case worker receives the stored financial data")
        void caseWorkerReceivesFinancialData() {
            Application app = applicationWithFinancialData(company, FINANCIAL_DATA);
            when(applicationRepository.findByIdWithDocuments(42L)).thenReturn(Optional.of(app));

            ApplicationDetailsResponse response = applicationService.viewApplication(
                    42L,
                    new CaseWorkerPrincipal(1L, "Karin Handläggare", "karin@resurs.se"));

            assertThat(response.financialData()).isEqualTo(FINANCIAL_DATA);
            verify(caseWorkerAssignmentService)
                    .ensureAssigned(eq(42L), any(CaseWorkerPrincipal.class));
        }

        @Test
        @DisplayName("Company does not receive financial data on its own application")
        void companyDoesNotReceiveFinancialData() {
            Application app = applicationWithFinancialData(company, FINANCIAL_DATA);
            when(applicationRepository.findByIdWithDocuments(42L)).thenReturn(Optional.of(app));

            ApplicationDetailsResponse response = applicationService.viewApplication(
                    42L,
                    new CompanyPrincipal(7L, "Testbolaget AB", "556677-8899"));

            assertThat(response.financialData()).isNull();
        }

        private static final String FINANCIAL_DATA =
                "{\"equity\":500000.0,\"totalCapital\":1000000.0,\"netRevenue\":1000000.0,"
                + "\"requestedAmount\":300000,\"industry\":\"IT\"}";
    }
}
