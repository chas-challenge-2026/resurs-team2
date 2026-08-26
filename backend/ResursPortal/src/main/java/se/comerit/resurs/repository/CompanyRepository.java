package se.comerit.resurs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import se.comerit.resurs.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {

}
