package se.comerit.resurs.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import se.comerit.resurs.config.PlainPiiCodec;
import se.comerit.resurs.config.PiiIndexProvider;
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
@Import({PiiAttributeConverter.class, AmountAttributeConverter.class, PlainPiiCodec.class, CompanyBlindIndexListener.class, DummyCryptoService.class, PiiIndexProvider.class})
@Sql(statements = {
        "DELETE FROM documents",
        "DELETE FROM applications",
        "DELETE FROM companies",
        "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (1, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Anders Karlsson')",
        "INSERT INTO applications (id, company_id, requested_amount, purpose, status, decision, scoring_result) VALUES (900, 1, '500000.00', 'Expansion av verksamheten', 'UNDER_REVIEW', null, 'FLAGGED: soliditet=0.28 (OK)')"
})
class ApplicationRepositoryTest {

    private static final Pageable pageable = PageRequest.of(0, 10);

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void shouldFindApplicationsByCompanyId() {
        Page<Application> result = applicationRepository.findByCompanyId(1L, pageable);

        assertThat(result.get()).hasSize(1);
        assertThat(result.getContent().getFirst()
                .getRequestedAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
    }

    @Test
    void shouldReturnEmptyForUnknownCompanyId() {
        Page<Application> result = applicationRepository.findByCompanyId(999L,pageable);

        assertThat(result.get()).isEmpty();

    }

    @Test
    void shouldFindApplicationsByStatus() {
        Page<Application> result = applicationRepository.findByStatus(ApplicationStatus.UNDER_REVIEW,pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus())
                .isEqualTo(ApplicationStatus.UNDER_REVIEW);
    }

    @Test
    void shouldReturnEmptyForUnknownStatus() {
        Page<Application> result = applicationRepository.findByStatus(ApplicationStatus.APPROVED,pageable);

        assertThat(result.getContent()).isEmpty();
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
    }

    @Test
    void shouldSetDefaultStatusOnNewApplication() {
        Company company = companyRepository.findByOrgNumber("556000-1234").orElseThrow();
        
        Application app = new Application(company, new BigDecimal("100000.00"), "Default status test");

        Application saved = applicationRepository.save(app);

        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.PENDING_DOCS);
    }

    @Test
    void shouldPaginateApplicationsByCompanyId() {
        Company company = companyRepository
                .findByOrgNumber("556000-1234")
                .orElseThrow();

        applicationRepository.saveAll(List.of(
                new Application(
                        company,
                        new BigDecimal("100000"),
                        "Application 1"
                ),
                new Application(
                        company,
                        new BigDecimal("200000"),
                        "Application 2"
                ),
                new Application(
                        company,
                        new BigDecimal("300000"),
                        "Application 3"
                )
        ));

        Pageable pageable = PageRequest.of(0, 2);

        Page<Application> result =
                applicationRepository.findByCompanyId(
                        company.getId(),
                        pageable
                );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isFalse();
    }

    @Test
    void shouldReturnSecondPage() {
        Company company = companyRepository
                .findByOrgNumber("556000-1234")
                .orElseThrow();

        applicationRepository.saveAll(List.of(
                new Application(company, new BigDecimal("100000"), "Application 1"),
                new Application(company, new BigDecimal("200000"), "Application 2"),
                new Application(company, new BigDecimal("300000"), "Application 3")
        ));

        Pageable pageable = PageRequest.of(1, 2);

        Page<Application> result =
                applicationRepository.findByCompanyId(
                        company.getId(),
                        pageable
                );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.isFirst()).isFalse();
        assertThat(result.isLast()).isTrue();
    }

}
