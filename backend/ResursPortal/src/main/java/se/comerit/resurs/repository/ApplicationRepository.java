package se.comerit.resurs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import se.comerit.resurs.entity.Application;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByCompanyId(Long companyId);
    List<Application> findByStatus(String status);
}
