package se.comerit.resurs.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import se.comerit.resurs.dto.DocumentDto;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Document;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.exception.EmptyFileException;
import se.comerit.resurs.exception.FileUploadException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.DocumentRepository;

import java.io.File;
import java.io.IOException;
import java.util.List;


@Service
public class DocumentService {

    private static final String UPLOAD_DIR = "/tmp/uploads/";


    private final ApplicationRepository applicationRepository;
    private final DocumentRepository documentRepository;

    public DocumentService(ApplicationRepository applicationRepository, DocumentRepository documentRepository) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
    }


    public List<DocumentDto> getDocuments(
            Long applicationId) {

        if (!applicationRepository.existsById(applicationId)) {
            throw new ApplicationNotFoundException(applicationId);
        }

        return documentRepository
                .findByApplicationIdOrderByUploadedAtDesc(applicationId)
                .stream()
                .map(DocumentDto::from)
                .toList();


    }

    public DocumentDto uploadDocument(
            Long applicationId,
            String docType,
            MultipartFile file) {

        validateFile(file);

        // Hämta application
        Application application = getApplication(applicationId);


        // Hämta original filename
        String originalFilename = getOriginalFilename(file);

        String storedFilename = createStoredFilename(applicationId, originalFilename);

        File destinationFile = prepareDestination(storedFilename);
        saveFile(file, destinationFile);

        Document document = saveDocument(
                application,
                storedFilename,
                docType
        );

        updateApplicationStatus(application, docType);

        applicationRepository.save(application);
        return DocumentDto.from(document);

    }

    public Resource downloadDocument(Long documentId) {

        Document document = documentRepository
                .findById(documentId)
                .orElseThrow(() ->
                        new RuntimeException("Document not found with ID: " + documentId)
                );

        File file = new File(
                UPLOAD_DIR,
                document.getFilename()
        );

        if (!file.exists()) {
            throw new RuntimeException(
                    "File not found: " +
                            document.getFilename()
            );
        }

        return new FileSystemResource(file);
    }


    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new EmptyFileException();
        }
    }

    private Application getApplication(Long applicationId) {

        return applicationRepository
                .findById(applicationId)
                .orElseThrow(() ->
                        new ApplicationNotFoundException(applicationId)
                );
    }


    // Filename handling
    private File prepareDestination(String storedFilename) {

        File uploadDir = new File(UPLOAD_DIR);

        if (!uploadDir.exists() && !uploadDir.mkdirs()) {
            throw new FileUploadException(
                    "Kunde inte skapa uppladdningskatalog."
            );
        }

        return new File(uploadDir, storedFilename);
    }

    private void saveFile(
            MultipartFile file,
            File destination) {

        try {
            file.transferTo(destination);
        } catch (IOException e) {
            throw new FileUploadException(
                    "Uppladdning misslyckades."

            );
        }
    }


    // File handling
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


    // Document
    private Document saveDocument(
            Application application,
            String storedFilename,
            String docType) {

        Document document = new Document(
                application,
                storedFilename,
                docType
        );

        return documentRepository.save(document);
    }


    // Application status
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

    public void deleteDocument(Long documentId) {

        Document document = documentRepository
                .findById(documentId)
                .orElseThrow(() ->
                        new ApplicationNotFoundException(documentId)
                );

        documentRepository.delete(document);
    }

}
