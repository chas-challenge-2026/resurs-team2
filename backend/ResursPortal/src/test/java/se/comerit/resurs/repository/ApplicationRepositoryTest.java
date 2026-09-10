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
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Company;

import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@Import({PiiAttributeConverter.class, AmountAttributeConverter.class, PlainPiiCodec.class, CompanyBlindIndexListener.class, DummyCryptoService.class})
@Sql(statements = {
        "DELETE FROM documents",
        "DELETE FROM applications",
        "DELETE FROM companies",
        "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (1, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Anders Karlsson')",
        "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, scoring_result, audit_log) VALUES (900, 1, 500000.00, 'Expansion av verksamheten', 'UNDER_REVIEW', null, 'FLAGGED: soliditet=0.28 (OK)', '[{\"ts\":\"2026-01-15T10:00:00\",\"action\":\"APPLICATION_CREATED\"}]')"
})
class ApplicationRepositoryTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void shouldFindApplicationsByCompanyId() {
        List<Application> result = applicationRepository.findByCompanyId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRequestedAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
    }

    @Test
    void shouldReturnEmptyForUnknownCompanyId() {
        List<Application> result = applicationRepository.findByCompanyId(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindApplicationsByStatus() {
        List<Application> result = applicationRepository.findByStatus(ApplicationStatus.UNDER_REVIEW);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
    }

    @Test
    void shouldReturnEmptyForUnknownStatus() {
        List<Application> result = applicationRepository.findByStatus(ApplicationStatus.APPROVED);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPersistNewApplication() {
        Company company = companyRepository.findByOrgNumber("556000-1234").orElseThrow();

        Application app = new Application(company, new BigDecimal("250000.00"), "Test loan");

        Application saved = applicationRepository.save(app);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.PENDING_DOCS);
        assertThat(saved.getAuditLog()).isEqualTo("[]");
    }

    @Test
    void shouldSetDefaultStatusOnNewApplication() {
        Company company = companyRepository.findByOrgNumber("556000-1234").orElseThrow();
        
        Application app = new Application(company, new BigDecimal("100000.00"), "Default status test");

        Application saved = applicationRepository.save(app);

        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.PENDING_DOCS);
    }
}
