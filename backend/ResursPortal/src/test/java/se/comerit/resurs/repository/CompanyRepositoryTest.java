package se.comerit.resurs.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import se.comerit.resurs.config.PlainPiiCodec;
import se.comerit.resurs.config.PiiIndexProvider;
import se.comerit.resurs.api.v1.service.DummyCryptoService;
import se.comerit.resurs.entity.CompanyBlindIndexListener;
import se.comerit.resurs.entity.PiiAttributeConverter;
import se.comerit.resurs.entity.Company;

import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@Import({PiiAttributeConverter.class, PlainPiiCodec.class, CompanyBlindIndexListener.class, DummyCryptoService.class, PiiIndexProvider.class})
@Sql(statements = {
        "DELETE FROM documents",
        "DELETE FROM applications",
        "DELETE FROM companies",
        "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (900, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Anders Karlsson')",
        "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (901, '556000-5678', X'a4f37788064f1cf726eadc704db91cdc0b1513e482981ff59641e13f518bbbea', 'Göteborg Handel AB', 'Maria Svensson')"
})
class CompanyRepositoryTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void shouldFindCompanyByOrgNumber() {
        Optional<Company> result = companyRepository.findByOrgNumber("556000-1234");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Malmö Fastigheter AB");
        assertThat(result.get().getAuthorizedSignatory()).isEqualTo("Anders Karlsson");
    }

    @Test
    void shouldReturnEmptyForUnknownOrgNumber() {
        Optional<Company> result = companyRepository.findByOrgNumber("000000-0000");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAllCompanies() {
        List<Company> companies = companyRepository.findAll();

        assertThat(companies).hasSize(2);
    }

    @Test
    void shouldPersistNewCompany() {
        Company company = new Company("556000-9999", "Test AB", "Test Person");

        Company saved = companyRepository.save(company);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrgNumberIndex()).isNotEmpty();
        assertThat(companyRepository.findByOrgNumber("556000-9999")).isPresent();
    }

    @Test
    void shouldThrowOnDuplicateOrgNumber() {
        Company duplicate = new Company("556000-1234", "Duplicate", "Nobody");

        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> companyRepository.save(duplicate)
        );
    }
}