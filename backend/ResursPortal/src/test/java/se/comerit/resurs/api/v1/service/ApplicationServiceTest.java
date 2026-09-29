package se.comerit.resurs.api.v1.service;



import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.argThat;

import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;

import static org.mockito.Mockito.never;

import static org.mockito.Mockito.times;

import static org.mockito.Mockito.verify;

import static org.mockito.Mockito.when;



import java.math.BigDecimal;

import java.time.Instant;

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



/\*\*

&#x20;\* Unit tests for {@link ApplicationService#submitApplication} and

&#x20;\* {@link ApplicationService#viewApplication}.

&#x20;\*

&#x20;\* \<p>

&#x20;\* Repositories and the {@link ScoringService} are mocked; the submission

&#x20;\* flow only persists the application with its serialized financial data, sends

&#x20;\* the "received" notification, and delegates the scoring to

&#x20;\* {@link ScoringService#scoreApplication}. A real {@link AuditLogService} is

&#x20;\* used so the APPLICATION_CREATED audit entry can be asserted for correctness.

&#x20;\*/

class ApplicationServiceTest {



&#x20;   private static final Instant FIXED_ETA = Instant.parse("2026-09-26T10:00:00Z");



&#x20;   private CompanyRepository companyRepository;

&#x20;   private ApplicationRepository applicationRepository;

&#x20;   private AuditLogRepository auditLogRepository;

&#x20;   private ScoringService scoringService;

&#x20;   private EtaService etaService;

&#x20;   private AuditLogService auditLogService;

&#x20;   private CaseWorkerAssignmentService caseWorkerAssignmentService;

&#x20;   private EmailService emailService;

&#x20;   private ApplicationService applicationService;

&#x20;   private ObjectMapper objectMapper;



&#x20;   private Company company;

&#x20;   private ApplicationRequest validRequest;



&#x20;   @BeforeEach

&#x20;   void setUp() {

&#x20;       companyRepository = mock(CompanyRepository.class);

&#x20;       applicationRepository = mock(ApplicationRepository.class);

&#x20;       auditLogRepository = mock(AuditLogRepository.class);

&#x20;       scoringService = mock(ScoringService.class);

&#x20;       objectMapper = new ObjectMapper();



&#x20;       auditLogService = new AuditLogService(

&#x20;               auditLogRepository,

&#x20;               mock(ApplicationRepository.class),

&#x20;               objectMapper);



&#x20;       caseWorkerAssignmentService = mock(CaseWorkerAssignmentService.class);



&#x20;       emailService = mock(EmailService.class);

&#x20;       etaService = mock(EtaService.class);



&#x20;       when(etaService.estimateWithinHours(

&#x20;               any(Instant.class),

&#x20;               eq(24L)))

&#x20;               .thenReturn(FIXED_ETA);



&#x20;       applicationService = new ApplicationService(

&#x20;               companyRepository,

&#x20;               applicationRepository,

&#x20;               scoringService,

&#x20;               etaService,

&#x20;               auditLogService,

&#x20;               caseWorkerAssignmentService,

&#x20;               objectMapper,

&#x20;               emailService,

&#x20;               null);



&#x20;       try {

&#x20;           var field = ApplicationService.class.getDeclaredField("self");

&#x20;           field.setAccessible(true);

&#x20;           field.set(applicationService, applicationService);

&#x20;       } catch (ReflectiveOperationException e) {

&#x20;           throw new IllegalStateException(

&#x20;                   "Failed to set self reference",

&#x20;                   e);

&#x20;       }



&#x20;       try {

&#x20;           var field = ApplicationService.class.getDeclaredField(

&#x20;                   "automatedDecisionHours");

&#x20;           field.setAccessible(true);

&#x20;           field.setLong(applicationService, 24L);

&#x20;       } catch (ReflectiveOperationException e) {

&#x20;           throw new IllegalStateException(

&#x20;                   "Failed to set automated decision SLA hours",

&#x20;                   e);

&#x20;       }



&#x20;       company = new Company(

&#x20;               "556677-8899",

&#x20;               "Testbolaget AB",

&#x20;               "Kalle Kula");



&#x20;       validRequest = new ApplicationRequest(

&#x20;               500_000.0,

&#x20;               1_000_000.0,

&#x20;               400_000.0,

&#x20;               200_000.0,

&#x20;               500_000.0,

&#x20;               150_000.0,

&#x20;               1_000_000.0,

&#x20;               new BigDecimal("300000"),

&#x20;               "Rörelsekapital",

&#x20;               120_000.0,

&#x20;               -50_000.0,

&#x20;               20_000.0,

&#x20;               "IT");

&#x20;   }



&#x20;   private void stubSaveReturnsSavedWithId(long id) {

&#x20;       when(applicationRepository.save(any(Application.class)))

&#x20;               .thenAnswer(invocation -> {

&#x20;                   Application app = invocation.getArgument(0);

&#x20;                   setApplicationId(app, id);

&#x20;                   return app;

&#x20;               });

&#x20;   }



&#x20;   // Application#id has no public setter; clear the id field reflectively to

&#x20;   // emulate the repository assigning a generated id on save.

&#x20;   private static void setApplicationId(

&#x20;           Application app,

&#x20;           long id) {



&#x20;       try {

&#x20;           var field = Application.class.getDeclaredField("id");

&#x20;           field.setAccessible(true);

&#x20;           field.set(app, id);

&#x20;       } catch (ReflectiveOperationException e) {

&#x20;           throw new IllegalStateException(

&#x20;                   "Failed to assign application id",

&#x20;                   e);

&#x20;       }

&#x20;   }



&#x20;   // Application#documents is populated by JPA; make it an empty list so the

&#x20;   // mapper can be exercised on a plain unit-test instance.

&#x20;   private static void setEmptyDocuments(Application app) {

&#x20;       try {

&#x20;           var field = Application.class.getDeclaredField("documents");

&#x20;           field.setAccessible(true);

&#x20;           field.set(app, List.of());

&#x20;       } catch (ReflectiveOperationException e) {

&#x20;           throw new IllegalStateException(

&#x20;                   "Failed to assign documents",

&#x20;                   e);

&#x20;       }

&#x20;   }



&#x20;   private Application applicationWithFinancialData(

&#x20;           Company owner,

&#x20;           String financialData) {



&#x20;       Application app = new Application(

&#x20;               owner,

&#x20;               new BigDecimal("300000"),

&#x20;               "Rörelsekapital",

&#x20;               ApplicationStatus.UNDER_REVIEW,

&#x20;               null,

&#x20;               null,

&#x20;               null,

&#x20;               financialData);



&#x20;       setEmptyDocuments(app);



&#x20;       return app;

&#x20;   }



&#x20;   @Nested

&#x20;   @DisplayName("Happy path")

&#x20;   class HappyPath {



&#x20;       @Test

&#x20;       @DisplayName("Submits the application, returns its id and delegates scoring")

&#x20;       void submitsAndDelegatesScoring() {

&#x20;           when(companyRepository.findByOrgNumber("556677-8899"))

&#x20;                   .thenReturn(Optional.of(company));



&#x20;           stubSaveReturnsSavedWithId(42L);



&#x20;           Long id = applicationService.submitApplication(

&#x20;                   "556677-8899",

&#x20;                   validRequest);



&#x20;           assertThat(id).isEqualTo(42L);



&#x20;           verify(scoringService)

&#x20;                   .scoreApplication(42L);



&#x20;           verify(applicationRepository)

&#x20;                   .save(any(Application.class));

&#x20;       }



&#x20;       @Test

&#x20;       @DisplayName("Persists the supplied company, requested amount, purpose and financial data")

&#x20;       void persistsCorrectFields() {

&#x20;           when(companyRepository.findByOrgNumber("556677-8899"))

&#x20;                   .thenReturn(Optional.of(company));



&#x20;           stubSaveReturnsSavedWithId(1L);



&#x20;           applicationService.submitApplication(

&#x20;                   "556677-8899",

&#x20;                   validRequest);



&#x20;           verify(applicationRepository)

&#x20;                   .save(argThat(app -> app.getCompany().equals(company)

&#x20;                           && app.getRequestedAmount()

&#x20;                                   .compareTo(

&#x20;                                           new BigDecimal("300000")) == 0

&#x20;                           && "Rörelsekapital"

&#x20;                                   .equals(app.getPurpose())

&#x20;                           && app.getStatus() == ApplicationStatus.SCORING_IN_PROGRESS

&#x20;                           && app.getFinancialData() != null

&#x20;                           && app.getFinancialData()

&#x20;                                   .contains(

&#x20;                                           "\\"industry\\":\\"IT\\"")

&#x20;                           && app.getEstimatedResolutionAt()

&#x20;                                   .equals(FIXED_ETA)));



&#x20;           verify(etaService)

&#x20;                   .estimateWithinHours(

&#x20;                           any(Instant.class),

&#x20;                           eq(24L));

&#x20;       }



&#x20;       @Test

&#x20;       @DisplayName("Sends an application-received email to the authorized signatory")

&#x20;       void sendsReceivedEmail() {

&#x20;           when(companyRepository.findByOrgNumber("556677-8899"))

&#x20;                   .thenReturn(Optional.of(company));



&#x20;           stubSaveReturnsSavedWithId(1L);



&#x20;           applicationService.submitApplication(

&#x20;                   "556677-8899",

&#x20;                   validRequest);



&#x20;           verify(emailService)

&#x20;                   .sendApplicationSubmitted(

&#x20;                           argThat(app -> app.getId().equals(1L)

&#x20;                                   && app.getCompany()

&#x20;                                           .getAuthorizedSignatory()

&#x20;                                           .equals(

&#x20;                                                   "Kalle Kula")));

&#x20;       }

&#x20;   }



&#x20;   @Nested

&#x20;   @DisplayName("Audit log")

&#x20;   class AuditLogEntries {



&#x20;       @Test

&#x20;       @DisplayName("Creates an APPLICATION_CREATED entry carrying the org number")

&#x20;       void applicationCreatedEntryPresent() {

&#x20;           when(companyRepository.findByOrgNumber("556677-8899"))

&#x20;                   .thenReturn(Optional.of(company));



&#x20;           stubSaveReturnsSavedWithId(1L);



&#x20;           applicationService.submitApplication(

&#x20;                   "556677-8899",

&#x20;                   validRequest);



&#x20;           ArgumentCaptor\<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);



&#x20;           verify(auditLogRepository, times(2))

&#x20;                   .save(captor.capture());



&#x20;           assertThat(captor.getAllValues())

&#x20;                   .hasSize(2);



&#x20;           assertThat(

&#x20;                   captor.getAllValues()

&#x20;                           .get(0)

&#x20;                           .getEntry())

&#x20;                   .contains(

&#x20;                           "\\"action\\":\\"APPLICATION_CREATED\\"")

&#x20;                   .contains(

&#x20;                           "\\"orgNumber\\":\\"556677-8899\\"");

&#x20;       }



&#x20;       @Test

&#x20;       @DisplayName("Creates an ETA_SET entry carrying the ISO-8601 estimated resolution time")

&#x20;       void etaSetEntryPresent() {

&#x20;           when(companyRepository.findByOrgNumber("556677-8899"))

&#x20;                   .thenReturn(Optional.of(company));



&#x20;           stubSaveReturnsSavedWithId(1L);



&#x20;           applicationService.submitApplication(

&#x20;                   "556677-8899",

&#x20;                   validRequest);



&#x20;           ArgumentCaptor\<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);



&#x20;           verify(auditLogRepository, times(2))

&#x20;                   .save(captor.capture());



&#x20;           assertThat(captor.getAllValues())

&#x20;                   .hasSize(2);



&#x20;           assertThat(

&#x20;                   captor.getAllValues()

&#x20;                           .get(1)

&#x20;                           .getEntry())

&#x20;                   .contains("\\"action\\":\\"ETA_SET\\"")

&#x20;                   .contains(

&#x20;                           "\\"estimatedResolutionAt\\":"

&#x20;                                   \+ "\\"2026-09-26T10:00:00Z\\"");

&#x20;       }

&#x20;   }



&#x20;   @Nested

&#x20;   @DisplayName("Failure cases")

&#x20;   class FailureCases {



&#x20;       @Test

&#x20;       @DisplayName("Does not save anything when the company is unknown")

&#x20;       void companyNotFoundNotSaved() {

&#x20;           when(companyRepository.findByOrgNumber("unknown"))

&#x20;                   .thenReturn(Optional.empty());



&#x20;           assertThatThrownBy(() -> applicationService.submitApplication(

&#x20;                   "unknown",

&#x20;                   validRequest))

&#x20;                   .isInstanceOf(

&#x20;                           CompanyNotFoundException.class)

&#x20;                   .hasMessage("Company not found")

&#x20;                   .hasMessageNotContaining("unknown");



&#x20;           verify(applicationRepository, never())

&#x20;                   .save(any(Application.class));



&#x20;           verify(scoringService, never())

&#x20;                   .scoreApplication(any());



&#x20;           verify(emailService, never())

&#x20;                   .sendApplicationSubmitted(

&#x20;                           any(Application.class));

&#x20;       }

&#x20;   }



&#x20;   @Nested

&#x20;   @DisplayName("View application financial data")

&#x20;   class ViewApplicationFinancialData {



&#x20;       @Test

&#x20;       @DisplayName("Case worker receives the stored financial data")

&#x20;       void caseWorkerReceivesFinancialData() {

&#x20;           Application app = applicationWithFinancialData(

&#x20;                   company,

&#x20;                   FINANCIAL_DATA);



&#x20;           when(applicationRepository

&#x20;                   .findByIdWithDocuments(42L))

&#x20;                   .thenReturn(Optional.of(app));



&#x20;           ApplicationDetailsResponse response = applicationService.viewApplication(

&#x20;                   42L,

&#x20;                   new CaseWorkerPrincipal(

&#x20;                           1L,

&#x20;                           "Karin Handläggare",

&#x20;                           "karin\@resurs.se"));



&#x20;           assertThat(response.financialData())

&#x20;                   .isEqualTo(FINANCIAL_DATA);



&#x20;           verify(caseWorkerAssignmentService)

&#x20;                   .ensureAssigned(

&#x20;                           eq(42L),

&#x20;                           any(CaseWorkerPrincipal.class));

&#x20;       }



&#x20;       @Test

&#x20;       @DisplayName("Company receives the stored financial data on its own application")

&#x20;       void companyReceivesFinancialData() {

&#x20;           Application app = applicationWithFinancialData(

&#x20;                   company,

&#x20;                   FINANCIAL_DATA);



&#x20;           when(applicationRepository

&#x20;                   .findByIdWithDocuments(42L))

&#x20;                   .thenReturn(Optional.of(app));



&#x20;           ApplicationDetailsResponse response = applicationService.viewApplication(

&#x20;                   42L,

&#x20;                   new CompanyPrincipal(

&#x20;                           7L,

&#x20;                           "Testbolaget AB",

&#x20;                           "556677-8899"));



&#x20;           assertThat(response.financialData())

&#x20;                   .isEqualTo(FINANCIAL_DATA);

&#x20;       }



&#x20;       private static final String FINANCIAL_DATA = "{\\"equity\\":500000.0,"

&#x20;               \+ "\\"totalCapital\\":1000000.0,"

&#x20;               \+ "\\"netRevenue\\":1000000.0,"

&#x20;               \+ "\\"requestedAmount\\":300000,"

&#x20;               \+ "\\"industry\\":\\"IT\\"}";

&#x20;   }

}