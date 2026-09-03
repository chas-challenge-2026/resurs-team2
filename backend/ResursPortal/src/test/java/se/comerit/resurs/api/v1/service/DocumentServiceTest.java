package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.internal.util.Primitives.defaultValue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import se.comerit.resurs.dto.DocumentDto;
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
import se.comerit.resurs.service.DocumentService;

class DocumentServiceTest {

    @Test
    void getDocuments_returnsDocumentsForMatchingApplication() {
        Company company = company("556677-8899");
        Application application = application(company, "Rörelsekapital");
        setId(application, 7L);

        Document older = new Document(application, "older.pdf", "AnnualReview");
        Document newer = new Document(application, "newer.pdf", "BankStatement");
        setId(older, 11L);
        setId(newer, 12L);

        Map<Long, Application> applications = new HashMap<>();
        applications.put(7L, application);

        Map<Long, Document> byId = new HashMap<>();
        byId.put(11L, older);
        byId.put(12L, newer);

        Map<Long, List<Document>> byApplication = new HashMap<>();
        byApplication.put(7L, List.of(newer, older));

        DocumentService service = new DocumentService(
                applicationRepository(applications, new AtomicLong(100)),
                documentRepository(byId, byApplication, new AtomicLong(1000)));

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
        setId(application, 7L);
        application.setStatus(ApplicationStatus.PENDING_DOCS);

        Map<Long, Application> applications = new HashMap<>();
        applications.put(7L, application);

        Map<Long, Document> documentsById = new HashMap<>();
        Map<Long, List<Document>> documentsByApplication = new HashMap<>();
        AtomicLong nextDocumentId = new AtomicLong(1L);

        DocumentService service = new DocumentService(
                applicationRepository(applications, new AtomicLong(50)),
                documentRepository(documentsById, documentsByApplication, nextDocumentId));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                "hello world".getBytes(StandardCharsets.UTF_8));

        DocumentDto saved = service.uploadDocument(
                7L,
                "AnnualReview",
                file,
                new CompanyPrincipal(1L, "customer", "556677-8899"));

        assertThat(saved.filename()).isEqualTo("7_report.pdf");
        assertThat(saved.docType()).isEqualTo("AnnualReview");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(documentsByApplication.get(7L)).hasSize(1);
    }

    @Test
    void uploadDocument_rejectsEmptyFile() {
        DocumentService service = new DocumentService(
                applicationRepository(new HashMap<>(), new AtomicLong(1L)),
                documentRepository(new HashMap<>(), new HashMap<>(), new AtomicLong(1L)));

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]);

        assertThatThrownBy(() ->
                service.uploadDocument(7L, "AnnualReview", emptyFile,
                        new CompanyPrincipal(1L, "customer", "556677-8899")))
                .isInstanceOf(EmptyFileException.class);
    }

    @Test
    void companyCannotAccessAnotherCompanyApplication() {
        Company otherCompany = company("111111-2222");
        Application application = application(otherCompany, "Expansion");
        setId(application, 7L);

        Map<Long, Application> applications = new HashMap<>();
        applications.put(7L, application);

        DocumentService service = new DocumentService(
                applicationRepository(applications, new AtomicLong(1L)),
                documentRepository(new HashMap<>(), new HashMap<>(), new AtomicLong(1L)));

        assertThatThrownBy(() ->
                service.getDocuments(7L, new CompanyPrincipal(1L, "customer", "556677-8899")))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void downloadDocument_rejectsForeignCompanyDocument() {
        Company otherCompany = company("111111-2222");
        Application application = application(otherCompany, "Expansion");
        setId(application, 7L);

        Document document = new Document(application, "foreign.pdf", "AnnualReview");
        setId(document, 21L);

        Map<Long, Application> applications = new HashMap<>();
        applications.put(7L, application);

        Map<Long, Document> byId = new HashMap<>();
        byId.put(21L, document);

        DocumentService service = new DocumentService(
                applicationRepository(applications, new AtomicLong(1L)),
                documentRepository(byId, Map.of(7L, List.of(document)), new AtomicLong(1L)));

        assertThatThrownBy(() ->
                service.downloadDocument(21L, new CompanyPrincipal(1L, "customer", "556677-8899")))
                .isInstanceOf(DocumentNotFoundException.class);
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
                                setId(app, nextId.getAndIncrement());
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
            Map<Long, Document> byId,
            Map<Long, List<Document>> byApplication,
            AtomicLong nextId) {

        return (DocumentRepository) Proxy.newProxyInstance(
                DocumentRepository.class.getClassLoader(),
                new Class<?>[] { DocumentRepository.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "findById" -> {
                            Long id = (Long) args[0];
                            return Optional.ofNullable(byId.get(id));
                        }
                        case "findByApplicationId", "findByApplicationIdOrderByUploadedAtDesc" -> {
                            Long appId = (Long) args[0];
                            return byApplication.getOrDefault(appId, List.of());
                        }
                        case "save" -> {
                            Document document = (Document) args[0];
                            if (document.getId() == null) {
                                setId(document, nextId.getAndIncrement());
                            }
                            byId.put(document.getId(), document);

                            Long applicationId = document.getApplication().getId();
                            byApplication.computeIfAbsent(applicationId, k -> new ArrayList<>())
                                    .add(document);

                            return document;
                        }
                        case "delete" -> {
                            Document document = (Document) args[0];
                            byId.remove(document.getId());
                            Long appId = document.getApplication().getId();
                            List<Document> list = byApplication.get(appId);
                            if (list != null) {
                                list.remove(document);
                            }
                            return null;
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> defaultValue(method.getReturnType());
                    }
                    return proxy;
                });
    }




    private static Company company(String orgNumber) {
        return new Company(orgNumber, "Testbolaget AB", "Kalle Kula");
    }

    private static Application application(Company company, String purpose) {
        return new Application(company, new BigDecimal("250000"), purpose);
    }

    private static void setId(Object target, long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to assign id", e);
        }
    }
}
