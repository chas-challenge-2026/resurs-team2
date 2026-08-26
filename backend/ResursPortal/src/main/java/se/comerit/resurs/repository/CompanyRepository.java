package se.comerit.resurs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import se.comerit.resurs.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByOrgNumber(String orgNumber);
}
