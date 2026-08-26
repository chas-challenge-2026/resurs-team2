package se.comerit.resurs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import se.comerit.resurs.entity.Application;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

}
