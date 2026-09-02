package se.comerit.resurs.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import se.comerit.resurs.dto.DocumentDto;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Document;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.DocumentRepository;

import java.io.File;
import java.io.IOException;
import java.util.List;



@Service
public class DocumentService {

    private static  final String UPLOAD_DIR = "/tmp/uploads/";

    private final ApplicationRepository applicationRepository;
    private final DocumentRepository documentRepository;

    public DocumentService(ApplicationRepository applicationRepository, DocumentRepository documentRepository) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
    }

    public ResponseEntity<List<DocumentDto>> getDocuments(
            Long applicationId) {

        if (!applicationRepository.existsById(applicationId)) {
            return ResponseEntity.notFound().build();
        }

        List<DocumentDto> documents = documentRepository
                .findByApplicationIdOrderByUploadedAtDesc(applicationId)
                .stream()
                .map(DocumentDto::from)
                .toList();

        return ResponseEntity.ok(documents);
    }

    public ResponseEntity<Object> uploadDocument(
            @RequestParam("applicationId") Long applicationId,
            @RequestParam("docType") String docType,
            @RequestParam("file") MultipartFile file) {

        // Kontrollera att fil skickades
        if (file.isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body("Ingen fil vald.");
        }

        // Hämta application
        Application application = applicationRepository
                .findById(applicationId)
                .orElse(null);

        if (application == null) {
            return ResponseEntity.notFound().build();
        }


        // Hämta original filename
        String originalFilename = file.getOriginalFilename() == null
                ? "upload.bin"
                : file.getOriginalFilename();

        // Skydda mot path traversal
        String safeFilename = originalFilename
                .replaceAll("[/\\\\]", "_");

        String storedFilename = applicationId + "_" + safeFilename;

        // Skapa upload directory
        File uploadDir = new File(UPLOAD_DIR);

        if (!uploadDir.exists() && !uploadDir.mkdirs()) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Kunde inte skapa uppladdningskatalog.");
        }

        File destination = new File(
                uploadDir,
                storedFilename
        );

        // Spara filen
        try {
            file.transferTo(destination);
        } catch (IOException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Uppladdning misslyckades: " + e.getMessage());
        }

        // Spara dokumentet i databasen
        Document document = documentRepository.save(
                new Document(
                        application,
                        storedFilename,
                        docType
                )
        );

        applicationRepository.save(application);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(DocumentDto.from(document));
    }

        public ResponseEntity<Resource> downloadDocument(Long documentId) {

            return documentRepository
                    .findById(documentId)
                    .map(this::createResourceResponse)
                    .orElseGet(() ->
                            ResponseEntity.notFound().build()
                    );
        }

    private String getOriginalFilename(MultipartFile file) {

        String filename = file.getOriginalFilename();

        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }

        return filename;
    }

    private String createStoredFilename(
            Long applicationId,
            String originalFilename) {

        String safeFilename = originalFilename
                .replaceAll("[/\\\\]", "_");

        return applicationId + "_" + safeFilename;
    }

    private File prepareDestination(String storedFilename) {
        File uploadDir = new File(UPLOAD_DIR);


        if (!uploadDir.exists() && !uploadDir.mkdirs()) {
            return null;
        }

        return new File(uploadDir, storedFilename);
    }


    private void updateApplicationStatus(
            Application application,
            String docType) {

        if ("arsredovisning".equals(docType)
                && application.getStatus()
                == ApplicationStatus.PENDING_DOCS) {

            application.setStatus(
                    ApplicationStatus.UNDER_REVIEW
            );
        }
    }

    private ResponseEntity<Resource> createResourceResponse(
            Document document) {

        String filename = document.getFilename();
        File file = new File(UPLOAD_DIR + filename);

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
