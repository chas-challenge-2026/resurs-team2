package se.comerit.resurs.api.v1.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import se.comerit.resurs.api.v1.dto.DocumentDto;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Document;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.exception.DocumentNotFoundException;
import se.comerit.resurs.exception.EmptyFileException;
import se.comerit.resurs.exception.FileUploadException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.DocumentRepository;
import se.comerit.resurs.security.CaseWorkerPrincipal;
import se.comerit.resurs.security.CompanyPrincipal;
import se.comerit.resurs.security.UserPrincipal;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentService {

    private static final String UPLOAD_DIR = "data";


    private final ApplicationRepository applicationRepository;
    private final DocumentRepository documentRepository;

    public DocumentService(ApplicationRepository applicationRepository, DocumentRepository documentRepository) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
    }

    public List<DocumentDto> getDocuments(
            Long applicationId,
            UserPrincipal principal) {
        Application application = getApplication(applicationId);
        checkApplicationAccess(application, principal);

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
            MultipartFile file,
            UserPrincipal principal) {

        validateFile(file);

        // Hämta application
        Application application = getApplication(applicationId);
        checkApplicationAccess(application, principal);

        // Hämta original filename
        String originalFilename = getOriginalFilename(file);

        String storedFilename = createStoredFilename(applicationId, originalFilename);

        String uuidFilename = createUuidFilename();

        File destinationFile = prepareDestination(storedFilename);
        File uuidFile = prepareDestination(uuidFilename);

        saveFile(file, destinationFile);
        saveFile(file, uuidFile);

        Document document = saveDocument(application, storedFilename, docType);
        updateApplicationStatus(application, docType);
        applicationRepository.save(application);
        return DocumentDto.from(document);

    }

    public Resource downloadDocument(Long documentId, UserPrincipal principal) {
        Document document = documentRepository
                .findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        checkDocumentAccess(document, principal);

        File file = new File( document.getFilename());

        if (!file.exists()) {
            throw new DocumentNotFoundException(documentId);
        }

        return new FileSystemResource(file);
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new EmptyFileException();
        }
        try {
            byte[] checkBytes = file.getBytes();

            if (checkBytes.length <= 4) {
                throw new FileUploadException("Only PDF files are allowed. File must be larger than 4 bytes.");
            }
            if (!isPdf(checkBytes)) {
                throw new FileUploadException("Only PDF files are allowed.");
            }
        } catch (IOException e) {
            throw new FileUploadException("Upload failed.");
        }
    }
        private Application getApplication(Long applicationId) {
           return applicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
    }

    // Prepare destination file, create upload directory if it does not exist
    private File prepareDestination(String storedFilename) {
        File uploadDir = new File(UPLOAD_DIR);

        if (!uploadDir.exists() && !uploadDir.mkdirs()) {
            throw new FileUploadException(
                    "Could not create upload directory.");
        }
        return new File(uploadDir, storedFilename);
    }

    private void saveFile(MultipartFile file, File destination) {
        try {
            file.transferTo(destination);
        } catch (IOException e) {
            throw new FileUploadException("Upload failed.");
        }
    }

    // Get original filename, if null or blank return default "upload.pdf"
    private String getOriginalFilename(MultipartFile file) {

        String filename = file.getOriginalFilename();

        if (filename == null || filename.isBlank()) {
            return "upload.pdf";
        }
        return filename;
    }


    private String createStoredFilename(Long applicationId,String originalFilename) {
        String safe = (originalFilename == null || originalFilename.isBlank())
                ? "document.pdf"
                : originalFilename.replace('\\', '/');

        safe = safe.substring(safe.lastIndexOf('/') + 1);
        safe = safe.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            safe = "document.pdf";
        }
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            safe += ".pdf";
        }

        return applicationId + "_" + UUID.randomUUID() + "_" + safe;
    }

    private String createUuidFilename() {
        return UUID.randomUUID() + ".pdf";
    }



    // Save document to database
    private Document saveDocument(Application application, String storedFilename, String docType) {
        Document document = new Document(application, storedFilename, docType);
        return documentRepository.save(document);
    }

    // Update application status based on document type
    private void updateApplicationStatus(Application application, String docType) {
        if ("AnnualReview".equals(docType)
                && application.getStatus() == ApplicationStatus.PENDING_DOCS) {
            application.setStatus(ApplicationStatus.UNDER_REVIEW);
        }
    }

    // delete document
    public void deleteDocument(Long documentId, UserPrincipal principal) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        checkDocumentAccess(document, principal);
        documentRepository.delete(document);
    }

    // Access control
    private void checkApplicationAccess(Application application, UserPrincipal principal) {
        boolean hasAccess = switch (principal) {
            case CompanyPrincipal companyPrincipal ->
                application.getCompany().getOrgNumber().equals(companyPrincipal.orgNumber());
            case CaseWorkerPrincipal ignored -> true;
        };

        if (!hasAccess) {
            throw new ApplicationNotFoundException(application.getId());
        }
    }

    private void checkDocumentAccess(Document document, UserPrincipal principal) {
        boolean hasAccess = switch (principal) {
            case CompanyPrincipal companyPrincipal ->
                document.getApplication().getCompany().getOrgNumber().equals(companyPrincipal.orgNumber());
            case CaseWorkerPrincipal ignored -> true;
        };
        if (!hasAccess) {
            throw new DocumentNotFoundException(document.getUuid());
        }

    }
    private boolean isPdf(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }
}
