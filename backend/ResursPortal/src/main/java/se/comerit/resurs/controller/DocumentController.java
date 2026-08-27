package se.comerit.resurs.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Document;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.DocumentRepository;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * DocumentController – Hanterar dokumentuppladdning.
 *
 * VARNING: PDF sparas men parsas INTE.
 * TODO: implement PDF parsing in v2 (see native/README.md)
 *
 * Anti-patterns:
 * - Filer sparas i /tmp/uploads — rensas vid omstart
 * - Ingen validering av filtyp (accepterar vad som helst)
 * - Audit log uppdateras via JSON string manipulation
 * - Session check copy-pasteat
 */
@Controller
public class DocumentController {

    private ApplicationRepository applicationRepository;
    private DocumentRepository documentRepository;

    // Uploads dir — /tmp rensas vid omstart, ingen persistent lagring
    // TODO: använd ett persistent filsystem eller S3 i v2
    private static final String UPLOAD_DIR = "/tmp/uploads/";

    public DocumentController(ApplicationRepository applicationRepository, DocumentRepository documentRepository) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/documents/{applicationId}")
    public String showDocumentsPage(@PathVariable("applicationId") Long applicationId,
            HttpSession session,
            Model model) {
        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null)
            return "redirect:/login";

        return applicationRepository.findById(applicationId).map(application -> {
            List<Document> documents = documentRepository.findByApplicationIdOrderByUploadedAtDesc(applicationId);

            model.addAttribute("application", application);
            model.addAttribute("documents", documents);
            model.addAttribute("applicationId", applicationId);
            return "documents";

        }).orElseGet(() -> "redirect:/applications");
    }

    @PostMapping("/document/upload")
    public String uploadDocument(@RequestParam("applicationId") Long applicationId,
            @RequestParam("docType") String docType,
            @RequestParam("file") MultipartFile file,
            HttpSession session,
            Model model) {
        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null)
            return "redirect:/login";

        if (file.isEmpty()) {
            model.addAttribute("error", "Ingen fil vald.");
            return "redirect:/documents/" + applicationId;
        }

        // No file type validation — accepts anything, not just PDF
        // TODO: validate that uploaded file is actually a PDF
        String originalFilename = file.getOriginalFilename();
        String storedFilename = applicationId + "_" + originalFilename;

        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        File destination = new File(UPLOAD_DIR + storedFilename);

        try {
            file.transferTo(destination);
        } catch (IOException e) {
            model.addAttribute("error", "Uppladdning misslyckades: " + e.getMessage());
            return "redirect:/documents/" + applicationId;
        }

        Application application = applicationRepository.findById(applicationId).orElseThrow();

        // Store filename in DB — file path is /tmp which is not persistent
        // TODO: implement PDF parsing in v2 (see native/README.md)
        // The file is saved but its contents are never read or validated
        Document newDocument = new Document(application, storedFilename, docType);
        documentRepository.save(newDocument);

        // Update audit log JSON blob — same string manipulation pattern as
        // ApplicationController
        // TODO: skapa separat audit_log-tabell med index
        String newEntry = "{\"ts\":\"" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "\",\"action\":\"DOCUMENT_UPLOADED\",\"filename\":\"" + originalFilename
                + "\",\"docType\":\"" + docType + "\"}";

        String currentLog = application.getAuditLog();

        String updatedLog;
        if (currentLog == null || currentLog.equals("[]")) {
            updatedLog = "[" + newEntry + "]";
        } else {
            updatedLog = currentLog.substring(0, currentLog.lastIndexOf("]")) + "," + newEntry + "]";
        }

        application.setAuditLog(updatedLog);
        applicationRepository.save(application);

        // Update application status from PENDING_DOCS to UNDER_REVIEW if årsredovisning
        // uploaded
        // No business rules validation — just check docType string
        if (("arsredovisning".equals(docType) || "årsredovisning".equals(docType))
                && application.getStatus() == ApplicationStatus.PENDING_DOCS) {
            application.setStatus(ApplicationStatus.UNDER_REVIEW);
            applicationRepository.save(application);
        }

        return "redirect:/documents/" + applicationId;
    }

    @GetMapping("/document/{id}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable("id") Long documentId,
            HttpSession session) {
        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null) {
            return ResponseEntity.status(302).header("Location", "/login").build();
        }

        ResponseEntity<Resource> response = (ResponseEntity<Resource>) documentRepository.findById(documentId)
                .map(document -> {
                    String filename = document.getFilename();
                    File file = new File(UPLOAD_DIR + filename);

                    if (!file.exists()) {
                        // File was in /tmp and got cleared on server restart
                        return ResponseEntity.notFound().build();
                    }

                    Resource resource = new FileSystemResource(file);

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(resource);

                }).orElseGet(() -> ResponseEntity.notFound().build());

        return response;
    }
}
