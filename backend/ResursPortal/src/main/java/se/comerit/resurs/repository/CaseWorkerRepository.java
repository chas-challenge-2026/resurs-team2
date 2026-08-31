package se.comerit.resurs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import se.comerit.resurs.entity.CaseWorker;

public interface CaseWorkerRepository extends JpaRepository<CaseWorker, Long> {
    Optional<CaseWorker> findByEmail(String email);
}
