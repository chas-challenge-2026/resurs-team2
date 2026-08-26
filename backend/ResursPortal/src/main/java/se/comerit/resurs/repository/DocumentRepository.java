package se.comerit.resurs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import se.comerit.resurs.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, Long> {

}
