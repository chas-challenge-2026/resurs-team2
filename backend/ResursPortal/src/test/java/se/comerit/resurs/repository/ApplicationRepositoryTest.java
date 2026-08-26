package se.comerit.resurs.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.Company;

@DataJpaTest
@ActiveProfiles("test")
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
        List<Application> result = applicationRepository.findByStatus("UNDER_REVIEW");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void shouldReturnEmptyForUnknownStatus() {
        List<Application> result = applicationRepository.findByStatus("NONEXISTENT");

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
        assertThat(saved.getStatus()).isEqualTo("PENDING_DOCS");
        assertThat(saved.getAuditLog()).isEqualTo("[]");
    }

    @Test
    void shouldSetDefaultStatusOnNewApplication() {
        Company company = companyRepository.findByOrgNumber("556000-1234").orElseThrow();

        Application app = new Application(company, new BigDecimal("100000.00"), "Default status test");

        Application saved = applicationRepository.save(app);

        assertThat(saved.getStatus()).isEqualTo("PENDING_DOCS");
    }
}
