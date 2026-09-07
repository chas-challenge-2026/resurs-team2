package se.comerit.resurs.api.v1.controller;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.entity.Document;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.exception.DocumentNotFoundException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.CompanyRepository;
import se.comerit.resurs.repository.DocumentRepository;
import se.comerit.resurs.security.CompanyPrincipal;
import se.comerit.resurs.security.WithCaseWorker;
import se.comerit.resurs.security.WithCompany;
import se.comerit.resurs.service.DocumentService;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:document_api;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private DocumentRepository documentRepository;

    private static final String COMPANY_A = "556000-1234";
    private static final String COMPANY_B = "556000-5678";

    private Application appA;
    private Application appB;
    private Document docA;
    private Document docB;

    @BeforeEach
    void setUp() throws IOException {
        documentRepository.deleteAll();
        applicationRepository.deleteAll();
        companyRepository.deleteAll();

        Path uploadDir = Path.of("/tmp/uploads");
        if (Files.exists(uploadDir)) {
            try (var paths = Files.walk(uploadDir)) {
                paths.filter(Files::isRegularFile)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new IllegalStateException("Failed cleaning upload dir", e);
                            }
                        });
            }
        }
        Files.createDirectories(uploadDir);

        Company companyA = companyRepository.save(new Company(COMPANY_A, "Company A", "Signer A"));
        Company companyB = companyRepository.save(new Company(COMPANY_B, "Company B", "Signer B"));

        appA = applicationRepository.save(new Application(companyA, new BigDecimal("250000.00"), "A working capital"));
        appB = applicationRepository.save(new Application(companyB, new BigDecimal("350000.00"), "B working capital"));

        docA = documentRepository.save(new Document(appA, "annual-review-" + appA.getId() + ".pdf", "AnnualReview"));
        docB = documentRepository.save(new Document(appB, "annual-review-" + appB.getId() + ".pdf", "AnnualReview"));
    }

    @Nested
    class listDocuments {
        @Test
        void unauthenticatedIs401() throws Exception {
            mockMvc.perform(get("/api/v1/applications/{id}/documents", appA.getId()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @WithCaseWorker
        void caseWorkerListsDocumentsForApplication() throws Exception {
            mockMvc.perform(get("/api/v1/applications/{id}/documents", appA.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].applicationId").value(appA.getId()))
                    .andExpect(jsonPath("$[0].docType").value("AnnualReview"));
        }

        @Test
        @WithCompany(orgNumber = COMPANY_A)
        void companyCanListOwnApplicationDocuments() throws Exception {
            mockMvc.perform(get("/api/v1/applications/{id}/documents", appA.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].applicationId").value(appA.getId()));
        }

        @Test
        @WithCompany(orgNumber = COMPANY_A)
        void companyCannotListOtherCompanyDocuments() throws Exception {
            mockMvc.perform(get("/api/v1/applications/{id}/documents", appB.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    class upload {
        @Test
        void unauthenticatedIs401() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "annual-review.pdf", MediaType.APPLICATION_PDF_VALUE, "hello".getBytes());

            mockMvc.perform(multipart("/api/v1/applications/{id}/documents", appA.getId())
                            .file(file)
                            .param("id", String.valueOf(appA.getId()))
                            .param("docType", "AnnualReview")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @WithCaseWorker
        void wrongRoleCaseWorkerIs403() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "annual-review.pdf", MediaType.APPLICATION_PDF_VALUE, "hello".getBytes());

            mockMvc.perform(multipart("/api/v1/applications/{id}/documents", appA.getId())
                            .file(file)
                            .param("id", String.valueOf(appA.getId()))
                            .param("docType", "AnnualReview")
                            .with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Access Denied"));
        }

        @Test
        @WithCompany(orgNumber = COMPANY_A)
        void companyCanUploadDocumentToOwnApplication() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "annual-review.pdf", MediaType.APPLICATION_PDF_VALUE, "hello world".getBytes());

            mockMvc.perform(multipart("/api/v1/applications/{id}/documents", appA.getId())
                            .file(file)
                            .param("id", String.valueOf(appA.getId()))
                            .param("docType", "AnnualReview")
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.applicationId").value(appA.getId()))
                    .andExpect(jsonPath("$.docType").value("AnnualReview"));
        }

        @Test
        @WithCompany(orgNumber = COMPANY_A)
        void companyCannotUploadToSomeoneElsesApplication() {
            CompanyPrincipal companyAPrincipal = new CompanyPrincipal(10L, "Company A", COMPANY_A);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "annual-review.pdf", MediaType.APPLICATION_PDF_VALUE, "hello world".getBytes());

            assertThatThrownBy(() -> documentService.uploadDocument(appB.getId(), "AnnualReview", file, companyAPrincipal))
                    .isInstanceOf(ApplicationNotFoundException.class);
        }
    }

    @Nested
    class download {
        @Test
        void unauthenticatedIs401() throws Exception {
            mockMvc.perform(get("/api/v1/documents/{id}", docA.getId()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @WithCompany(orgNumber = COMPANY_A)
        void companyCanDownloadOwnDocument() throws Exception {
            Files.write(Path.of("/tmp/uploads", docA.getFilename()), "hello".getBytes());

            mockMvc.perform(get("/api/v1/documents/{id}", docA.getId()))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment"));
        }

        @Test
        @WithCompany(orgNumber = COMPANY_A)
        void companyCannotDownloadOtherCompanyDocument() {
            CompanyPrincipal companyAPrincipal = new CompanyPrincipal(10L, "Company A", COMPANY_A);

            assertThatThrownBy(() -> documentService.downloadDocument(docB.getId(), companyAPrincipal))
                    .isInstanceOf(DocumentNotFoundException.class);
        }

        @Test
        @WithCaseWorker
        void caseWorkerCanDownloadAnyDocument() throws Exception {
            Files.write(Path.of("/tmp/uploads", docA.getFilename()), "hello".getBytes());

            mockMvc.perform(get("/api/v1/documents/{id}", docA.getId()))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment"));
        }
    }

    @Nested
    class deleteDocument {
        @Test
        void unauthenticatedIs401() throws Exception {
            mockMvc.perform(delete("/api/v1/documents/{id}", docA.getId()).with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @WithCaseWorker
        void caseWorkerCanDeleteDocument() throws Exception {
            Files.write(Path.of("/tmp/uploads", docA.getFilename()), "hello".getBytes());

            mockMvc.perform(delete("/api/v1/documents/{id}", docA.getId()).with(csrf()))
                    .andExpect(status().isNoContent());

            assertThat(documentRepository.findById(docA.getId())).isEmpty();
        }

        @Test
        @WithCompany(orgNumber = COMPANY_A)
        void companyCanDeleteOwnDocument() throws Exception {
            Files.write(Path.of("/tmp/uploads", docA.getFilename()), "hello".getBytes());

            mockMvc.perform(delete("/api/v1/documents/{id}", docA.getId()).with(csrf()))
                    .andExpect(status().isNoContent());

            assertThat(documentRepository.findById(docA.getId())).isEmpty();
        }

        @Test
        @WithCompany(orgNumber = COMPANY_A)
        void companyCannotDeleteOtherCompanyDocument() {
            CompanyPrincipal companyAPrincipal = new CompanyPrincipal(10L, "Company A", COMPANY_A);

            assertThatThrownBy(() -> documentService.deleteDocument(docB.getId(), companyAPrincipal))
                    .isInstanceOf(DocumentNotFoundException.class);
        }
    }
}
