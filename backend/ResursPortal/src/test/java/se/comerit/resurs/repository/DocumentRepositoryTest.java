package se.comerit.resurs.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import se.comerit.resurs.config.PlainPiiCodec;
import se.comerit.resurs.api.v1.service.DummyCryptoService;
import se.comerit.resurs.entity.CompanyBlindIndexListener;
import se.comerit.resurs.entity.PiiAttributeConverter;
import se.comerit.resurs.entity.AmountAttributeConverter;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.entity.Document;

import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@Import({PiiAttributeConverter.class, AmountAttributeConverter.class, PlainPiiCodec.class, CompanyBlindIndexListener.class, DummyCryptoService.class})
@Sql(statements = {
        "DELETE FROM documents",
        "DELETE FROM applications",
        "DELETE FROM companies",
        "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (900, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Anders Karlsson')"
})
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void shouldFindDocumentsByApplicationId() {
        List<Document> result = documentRepository.findByApplicationId(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownApplicationId() {
        List<Document> result = documentRepository.findByApplicationId(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPersistNewDocument() {
        Company company = companyRepository.findByOrgNumber("556000-1234").orElseThrow();
        Application savedApp = applicationRepository.save(
            new Application(company, new BigDecimal("100000.00"), "Document test"));

        Document doc = new Document(savedApp, "test-file.pdf", "INVOICE");

        Document savedDoc = documentRepository.save(doc);

        assertThat(savedDoc.getId()).isNotNull();
        assertThat(savedDoc.getUploadedAt()).isNotNull();
        assertThat(documentRepository.findByApplicationId(savedApp.getId())).hasSize(1);
    }

    @Test
    void shouldSetTimestampOnPersist() {
        Company company = companyRepository.findByOrgNumber("556000-1234").orElseThrow();
        Application savedApp = applicationRepository.save(
            new Application(company, new BigDecimal("50000.00"), "Timestamp test"));

        Document doc = new Document(savedApp, "timestamp-test.pdf", "CONTRACT");

        Document saved = documentRepository.save(doc);

        assertThat(saved.getUploadedAt()).isNotNull();
    }
}
