package se.comerit.resurs.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


import org.springframework.data.jpa.repository.JpaRepository;
import se.comerit.resurs.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByApplicationId(Long applicationId);
    List<Document> findByApplicationIdOrderByUploadedAtDesc(Long applicationId);


    Optional<Document>findByUuid(UUID uuid);
}
