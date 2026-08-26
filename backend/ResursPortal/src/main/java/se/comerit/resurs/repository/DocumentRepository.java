package se.comerit.resurs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import se.comerit.resurs.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByApplicationId(Long applicationId);
}
