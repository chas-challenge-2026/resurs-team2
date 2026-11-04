package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.util.Primitives.defaultValue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import se.comerit.resurs.audit.EtaSet;
import se.comerit.resurs.api.v1.dto.DocumentDto;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.entity.Document;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.exception.DocumentNotFoundException;
import se.comerit.resurs.exception.EmptyFileException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.DocumentRepository;
import se.comerit.resurs.security.CompanyPrincipal;

class DocumentServiceTest {

    private static final Instant FIXED_ETA = Instant.parse("2026-09-26T10:00:00Z");

    private static FileStorageService stubStorage() {
        return new FileStorageService() {
            private final Map<String, byte[]> files = new HashMap<>();

            @Override
            public String upload(java.util.UUID documentId, String originalFilename, InputStream inputStream, long size)
                    throws IOException {
                String key = documentId + ".pdf";
                files.put(key, inputStream.readAllBytes());
                return key;
            }

            @Override
            public Resource download(String storedFilename) throws IOException {
                byte[] content = files.get(storedFilename);
                if (content == null)
                    throw new java.io.FileNotFoundException(storedFilename);
                return new ByteArrayResource(content);
            }

            @Override
            public void delete(String storedFilename) throws IOException {
                files.remove(storedFilename);
            }
        };
    }

    private static void setReviewBusinessDays(DocumentService service) {
        try {
            Field field = DocumentService.class.getDeclaredField("reviewBusinessDays");
            field.setAccessible(true);
            field.setInt(service, 2);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set review business days", e);
        }
    }

    @Test
    void getDocuments_returnsDocumentsForMatchingApplication() {
        Company company = company("556677-8899");
        Application application = application(company, "Rörelsekapital");
        setId(application);

        Document older = new Document(application, "older.pdf", "AnnualReview");
        Document newer = new Document(application, "newer.pdf", "BankStatement");

        Map<Long, Application> applications = new HashMap<>();
        applications.put(7L, application);

        Map<UUID, Document> byId = new HashMap<>();
        byId.put(new UUID(0L, 11L), older);
        byId.put(new UUID(0L, 12L), newer);

        Map<Long, List<Document>> byApplication = new HashMap<>();
        byApplication.put(7L, List.of(newer, older));

        EmailService emailService = mock(EmailService.class);

        DocumentService service = new DocumentService(
                applicationRepository(applications, new AtomicLong(100)),
                documentRepository(byId, byApplication, new AtomicLong(1000)),
                emailService,
                mock(AuditLogService.class), mock(EtaService.class),
                stubStorage());

        List<DocumentDto> result = service.getDocuments(
                7L,
                new CompanyPrincipal(1L, "customer", "556677-8899"));

        assertThat(result).extracting(DocumentDto::filename)
                .containsExactly("newer.pdf", "older.pdf");
    }

    @Test
    void uploadDocument_savesFile_andUpdatesApplicationStatus() {
        Company company = company("556677-8899");
        Application application = application(company, "Rörelsekapital");
        setId(application);
        application.setStatus(ApplicationStatus.PENDING_DOCS);

        Map<Long, Application> applications = new HashMap<>();
        applications.put(7L, application);

        Map<UUID, Document> documentsById = new HashMap<>();
        Map<Long, List<Document>> documentsByApplication = new HashMap<>();
        AtomicLong nextDocumentId = new AtomicLong(1L);

        EmailService emailService = mock(EmailService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        EtaService etaService = mock(EtaService.class);
        when(etaService.estimateBusinessDays(any(Instant.class), eq(2))).thenReturn(FIXED_ETA);

        DocumentService service = new DocumentService(
                applicationRepository(applications, new AtomicLong(50)),
                documentRepository(documentsById, documentsByApplication, nextDocumentId),
                emailService,
                auditLogService, etaService,
                stubStorage());
        setReviewBusinessDays(service);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.UTF_8));

        DocumentDto saved = service.uploadDocument(
                7L,
                "AnnualReview",
                file,
                new CompanyPrincipal(1L, "customer", "556677-8899"));

        assertThat(saved.filename())
                .isEqualTo("report.pdf");

        assertThat(saved.docType())
                .isEqualTo("AnnualReview");

        assertThat(application.getStatus())
                .isEqualTo(ApplicationStatus.UNDER_REVIEW);

        // Flagged applications get a manual-review ETA and an ETA_SET entry.
        assertThat(application.getEstimatedResolutionAt())
                .isEqualTo(FIXED_ETA);
        verify(etaService).estimateBusinessDays(any(Instant.class), eq(2));
        verify(auditLogService).append(application, new EtaSet("2026-09-26T10:00:00Z"));

        assertThat(documentsByApplication.get(7L))
                .hasSize(1);
    }

    @Test
    void uploadDocument_rejectsEmptyFile() {
        EmailService emailService = mock(EmailService.class);

        DocumentService service = new DocumentService(
                applicationRepository(new HashMap<>(), new AtomicLong(1L)),
                documentRepository(new HashMap<>(), new HashMap<>(), new AtomicLong(1L)),
                emailService,
                mock(AuditLogService.class), mock(EtaService.class),
                stubStorage());

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]);

        assertThatThrownBy(() -> service.uploadDocument(7L, "AnnualReview", emptyFile,
                new CompanyPrincipal(1L, "customer", "556677-8899")))
                .isInstanceOf(EmptyFileException.class);
    }

    @Test
    void companyCannotAccessAnotherCompanyApplication() {
        Company otherCompany = company("111111-2222");
        Application application = application(otherCompany, "Expansion");
        setId(application);

        Map<Long, Application> applications = new HashMap<>();
        applications.put(7L, application);

        EmailService emailService = mock(EmailService.class);

        DocumentService service = new DocumentService(
                applicationRepository(applications, new AtomicLong(1L)),
                documentRepository(new HashMap<>(), new HashMap<>(), new AtomicLong(1L)),
                emailService,
                mock(AuditLogService.class), mock(EtaService.class),
                stubStorage());

        assertThatThrownBy(() -> service.getDocuments(7L, new CompanyPrincipal(1L, "customer", "556677-8899")))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void downloadDocument_rejectsForeignCompanyDocument() {
        Company otherCompany = company("111111-2222");
        setId(otherCompany);
        Application application = application(otherCompany, "Expansion");
        setId(application);

        Document document = new Document(application, "foreign.pdf", "AnnualReview");
        setUuid(document, 21L);

        Map<Long, Application> applications = new HashMap<>();
        applications.put(7L, application);

        Map<UUID, Document> byId = new HashMap<>();
        byId.put(new UUID(0L, 21L), document);

        EmailService emailService = mock(EmailService.class);

        DocumentService service = new DocumentService(
                applicationRepository(applications, new AtomicLong(1L)),
                documentRepository(byId, Map.of(7L, List.of(document)), new AtomicLong(1L)),
                emailService,
                mock(AuditLogService.class), mock(EtaService.class),
                stubStorage());

        assertThatThrownBy(() -> service.downloadDocument(new UUID(0L, 21L),
                new CompanyPrincipal(1L, "customer", "556677-8899")))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void uploadDocument_twoFilesWithSameName() throws IOException {
        Company company = company("556677-8899");

        Application application = application(company, "Rörelsekapital");
        setId(application);
        application.setStatus(ApplicationStatus.PENDING_DOCS);

        Map<Long, Application> applications = new HashMap<>();
        applications.put(7L, application);

        Map<UUID, Document> documentsById = new HashMap<>();
        Map<Long, List<Document>> documentsByApplication = new HashMap<>();
        AtomicLong nextDocumentId = new AtomicLong(1L);

        EmailService emailService = mock(EmailService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        EtaService etaService = mock(EtaService.class);
        when(etaService.estimateBusinessDays(any(Instant.class), eq(2))).thenReturn(FIXED_ETA);

        DocumentService service = new DocumentService(
                applicationRepository(applications, new AtomicLong(50)),
                documentRepository(
                        documentsById,
                        documentsByApplication,
                        nextDocumentId),
                emailService,
                auditLogService, etaService,
                stubStorage());
        setReviewBusinessDays(service);

        String content1 = "%PDF-1.4 first file";
        String content2 = "%PDF-1.4 second file";

        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                content1.getBytes(StandardCharsets.UTF_8));

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                content2.getBytes(StandardCharsets.UTF_8));

        CompanyPrincipal principal = new CompanyPrincipal(1L, "customer", "556677-8899");

        DocumentDto saved1 = service.uploadDocument(
                7L,
                "AnnualReview",
                file1,
                new CompanyPrincipal(1L, "customer", "556677-8899"));

        DocumentDto saved2 = service.uploadDocument(
                7L,
                "AnnualReview",
                file2,
                new CompanyPrincipal(1L, "customer", "556677-8899"));

        assertThat(saved1.filename())
                .isEqualTo("report.pdf");

        assertThat(saved2.filename())
                .isEqualTo("report.pdf");

        // Same original filename must not collide on disk: storage keys stay distinct
        List<Document> stored = documentsByApplication.get(7L);
        assertThat(stored).hasSize(2);
        assertThat(stored.get(0).getFilename())
                .isNotEqualTo(stored.get(1).getFilename());

        DocumentService.DocumentDownload downloaded1 =
                service.downloadDocument(saved1.uuid(), principal);

        DocumentService.DocumentDownload downloaded2 =
                service.downloadDocument(saved2.uuid(), principal);

        assertThat(downloaded1.originalFilename())
                .isEqualTo("report.pdf");
        assertThat(downloaded2.originalFilename())
                .isEqualTo("report.pdf");

        assertThat(new String(
                downloaded1.resource().getInputStream().readAllBytes(),
                StandardCharsets.UTF_8))
                .isEqualTo(content1);

        assertThat(new String(
                downloaded2.resource().getInputStream().readAllBytes(),
                StandardCharsets.UTF_8))
                .isEqualTo(content2);
    }

    @Test
    void uploadDocumentOnScoringInProgress_keepsStatusAndEta() {
        Company company = company("556677-8899");
        Application application = application(company, "Rörelsekapital");
        setId(application);
        application.setStatus(ApplicationStatus.SCORING_IN_PROGRESS);
        application.setEstimatedResolutionAt(FIXED_ETA);

        Map<Long, Application> applications = new HashMap<>();
        applications.put(7L, application);

        Map<UUID, Document> documentsById = new HashMap<>();
        Map<Long, List<Document>> documentsByApplication = new HashMap<>();
        AtomicLong nextDocumentId = new AtomicLong(1L);

        EmailService emailService = mock(EmailService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        DocumentService service = new DocumentService(
                applicationRepository(applications, new AtomicLong(50)),
                documentRepository(documentsById, documentsByApplication, nextDocumentId),
                emailService,
                auditLogService, mock(EtaService.class),
                stubStorage());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        service.uploadDocument(
                7L,
                "AnnualReview",
                file,
                new CompanyPrincipal(1L, "customer", "556677-8899"));

        // Scoring owns the SCORING_IN_PROGRESS -> UNDER_REVIEW transition; a
        // document upload must not move the status or touch the ETA.
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SCORING_IN_PROGRESS);
        assertThat(application.getEstimatedResolutionAt()).isEqualTo(FIXED_ETA);
        verify(auditLogService, never()).append(any(), any(EtaSet.class));
    }

    private static ApplicationRepository applicationRepository(
            Map<Long, Application> applications,
            AtomicLong nextId) {

        return (ApplicationRepository) Proxy.newProxyInstance(
                ApplicationRepository.class.getClassLoader(),
                new Class<?>[] { ApplicationRepository.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "findById" -> {
                            Long id = (Long) args[0];
                            return Optional.ofNullable(applications.get(id));
                        }
                        case "existsById" -> {
                            Long id = (Long) args[0];
                            return applications.containsKey(id);
                        }
                        case "save" -> {
                            Application app = (Application) args[0];
                            if (app.getId() == null) {
                                setUuid(app, nextId.getAndIncrement());
                            }
                            applications.put(app.getId(), app);
                            return app;
                        }
                        case "delete" -> {
                            Application app = (Application) args[0];
                            applications.remove(app.getId());
                            return null;
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> defaultValue(method.getReturnType());
                    }
                    return proxy;
                });
    }

    private static DocumentRepository documentRepository(
            Map<UUID, Document> byId,
            Map<Long, List<Document>> byApplication,
            AtomicLong nextId) {

        return (DocumentRepository) Proxy.newProxyInstance(
                DocumentRepository.class.getClassLoader(),
                new Class<?>[] { DocumentRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();

                    if ("findById".equals(name) || "findByUuid".equals(name)) {
                        UUID id = (UUID) args[0];
                        return Optional.ofNullable(byId.get(id));
                    }

                    if ("findByApplicationId".equals(name) || "findByApplicationIdOrderByUploadedAtDesc".equals(name)) {
                        Long appId = (Long) args[0];
                        return byApplication.getOrDefault(appId, List.of());
                    }

                    if ("save".equals(name)) {
                        Document document = (Document) args[0];
                        if (document.getUuid() == null) {
                            setUuid(document, nextId.getAndIncrement());
                        }
                        byId.put(document.getUuid(), document);

                        Long applicationId = document.getApplication().getId();
                        byApplication.computeIfAbsent(applicationId, k -> new ArrayList<>()).add(document);
                        return document;
                    }

                    if ("delete".equals(name)) {
                        Document document = (Document) args[0];
                        byId.remove(document.getUuid());
                        Long appId = document.getApplication().getId();
                        List<Document> list = byApplication.get(appId);
                        if (list != null)
                            list.remove(document);
                        return null;
                    }

                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }

                    if (args != null && args.length == 1 && args[0] instanceof UUID id) {
                        return Optional.ofNullable(byId.get(id));
                    }

                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class)
                return false;
            if (type == int.class)
                return 0;
            if (type == long.class)
                return 0L;
            if (type == double.class)
                return 0.0;
            if (type == float.class)
                return 0.0f;
            if (type == short.class)
                return (short) 0;
            if (type == byte.class)
                return (byte) 0;
            if (type == char.class)
                return '\0';
        }
        return null;
    }

    private static Company company(String orgNumber) {

        return new Company(orgNumber, "Testbolaget AB", "Kalle Kula");
    }

    private static Application application(Company company, String purpose) {
        return new Application(company, new BigDecimal("250000"), purpose);
    }

    private static void setId(Object target) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, (Long) 7L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to assign id", e);
        }
    }

    private static void setUuid(Object target, Long uuid) {
        try {
            Field field = target.getClass().getDeclaredField("uuid");
            field.setAccessible(true);
            if (uuid == null) {
                field.set(target, null);
            } else {
                field.set(target, new java.util.UUID(0L, uuid));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to assign uuid", e);
        }
    }

}
