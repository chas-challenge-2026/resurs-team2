package se.comerit.resurs.api.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import se.comerit.resurs.api.v1.dto.DocumentDto;
import se.comerit.resurs.api.v1.service.DocumentService;
import se.comerit.resurs.security.UserPrincipal;

import java.util.List;
import java.util.UUID;


/**
 * DocumentController – Hanterar dokumentuppladdning.
 * <p>
 * VARNING: PDF sparas men parsas INTE.
 * TODO: implement PDF parsing in v2 (see native/README.md)
 * <p>
 * Anti-patterns:
 * - Ingen validering av filtyp (accepterar vad som helst)
 * - Audit log uppdateras via JSON string manipulation
 * - Session check copy-pasteat
 * <p>
 * Lagring: filer sparas via FileStorageService (local disk eller S3),
 * se storage.type i application.properties. Filer krypteras i vila via
 * EncryptedFileStorageService (nativ AES-256-GCM) om storage.encryption.enabled=true.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Documents", description = "Upload, list, download, and delete application documents")
@SecurityRequirement(name = "Bearer Authentication")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PreAuthorize("hasAnyRole('COMPANY', 'CASE_WORKER')")
    @GetMapping("applications/{id}/documents")
    @Operation(
        summary = "List application documents",
        description = "Return metadata for all documents uploaded to an application. "
                + "Companies may only view documents for their own applications.")
    @ApiResponse(responseCode = "200", description = "Document metadata list returned")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "Caller is not authorised to view these documents")
    @ApiResponse(responseCode = "404", description = "Application not found")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<List<DocumentDto>> getDocuments(
            @Parameter(description = "Unique application ID", example = "1")
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        List<DocumentDto> documents =documentService.getDocuments(id,principal);
        return ResponseEntity.ok(documents);
    }

    @PreAuthorize("hasRole('COMPANY')")
    @PostMapping(
            path = "applications/{id}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload a document",
        description = "Upload a document file (multipart form data) for an application. "
                + "Requires the COMPANY role.")
    @ApiResponse(responseCode = "201", description = "Document uploaded; returns the new document metadata")
    @ApiResponse(responseCode = "400", description = "Missing file, missing/invalid docType, or invalid application ID")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "Caller does not have the COMPANY role")
    @ApiResponse(responseCode = "404", description = "Application not found")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<DocumentDto> uploadDocument(
            @Parameter(description = "Unique application ID", example = "1")
            @PathVariable Long id,
            @RequestParam String docType,
            @RequestParam MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        DocumentDto document = documentService.uploadDocument(
                id,
                docType,
                file,
                principal
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(document);
    }

    @PreAuthorize("hasAnyRole('COMPANY', 'CASE_WORKER')")
    @GetMapping("/documents/{id}")
    @Operation(
        summary = "Download a document",
        description = "Download the file content of a stored document as application/pdf.")
    @ApiResponse(responseCode = "200", description = "Document file content returned as PDF")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "Caller is not authorised to download this document")
    @ApiResponse(responseCode = "404", description = "Document not found")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<Resource> downloadDocument(
            @Parameter(description = "Unique document identifier",
                       example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
            @AuthenticationPrincipal UserPrincipal principal) {

        DocumentService.DocumentDownload download = documentService.downloadDocument(id, principal);

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(download.originalFilename())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(download.resource());
    }

    @PreAuthorize("hasAnyRole('COMPANY', 'CASE_WORKER')")
    @DeleteMapping("/documents/{id}")
    @Operation(
        summary = "Delete a document",
        description = "Permanently delete a stored document and its metadata.")
    @ApiResponse(responseCode = "204", description = "Document deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "Caller is not authorised to delete this document")
    @ApiResponse(responseCode = "404", description = "Document not found")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Unique document identifier",
                       example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        documentService.deleteDocument(id, principal);

        return ResponseEntity.noContent().build();
    }

}