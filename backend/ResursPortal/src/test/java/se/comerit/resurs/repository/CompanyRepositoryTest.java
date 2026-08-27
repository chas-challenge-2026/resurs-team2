package se.comerit.resurs.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import se.comerit.resurs.entity.Company;

@DataJpaTest
@ActiveProfiles("test")
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
