package se.comerit.resurs.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import se.comerit.resurs.dto.DocumentDto;
import se.comerit.resurs.security.UserPrincipal;
import se.comerit.resurs.service.DocumentService;

import java.util.List;


/**
 * DocumentController – Hanterar dokumentuppladdning.
 * <p>
 * VARNING: PDF sparas men parsas INTE.
 * TODO: implement PDF parsing in v2 (see native/README.md)
 * <p>
 * Anti-patterns:
 * - Filer sparas i /tmp/uploads — rensas vid omstart
 * - Ingen validering av filtyp (accepterar vad som helst)
 * - Audit log uppdateras via JSON string manipulation
 * - Session check copy-pasteat
 */
@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PreAuthorize("hasRole('CASE_WORKER')")
    @GetMapping("applications/{id}/documents")
    public ResponseEntity<List<DocumentDto>> getDocuments(
            @PathVariable Long id) {

        List<DocumentDto> documents =
                documentService.getDocuments(id);

        return ResponseEntity.ok(documents);
    }

    @PreAuthorize("hasRole('COMPANY')")
    @PostMapping(
            path = "applications/{id}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)

    public ResponseEntity<DocumentDto> uploadDocument(
            @RequestParam Long id,
            @RequestParam String docType,
            @RequestParam MultipartFile file) {

        DocumentDto document = documentService.uploadDocument(
                id,
                docType,
                file
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(document);
    }

    @PreAuthorize("hasAnyRole('COMPANY', 'CASE_WORKER')")
    @GetMapping("/documents/{id}")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        Resource resource = documentService.downloadDocument(id);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment"
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PreAuthorize("hasRole('CASE_WORKER')")
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id) {

        documentService.deleteDocument(id);

        return ResponseEntity.noContent().build();
    }

}










