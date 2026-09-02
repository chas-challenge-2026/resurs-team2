package se.comerit.resurs.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.core.io.FileSystemResource;
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
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Document;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.DocumentRepository;
import se.comerit.resurs.security.UserPrincipal;
import se.comerit.resurs.service.DocumentService;

import java.io.File;
import java.io.IOException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.springframework.data.jpa.domain.AbstractPersistable_.id;


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
@RequestMapping("/api")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PreAuthorize("hasRole('CASE_WORKER')")
    @GetMapping("/documents/{applicationId}")
    public ResponseEntity<List<DocumentDto>> getDocuments(
            @PathVariable Long applicationId) {

        return documentService.getDocuments(applicationId);
    }

    @PreAuthorize("hasRole('COMPANY')")
    @PostMapping(
            path = "/document/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<DocumentDto> uploadDocument(
            @RequestParam Long applicationId,
            @RequestParam String docType,
            @RequestParam MultipartFile file) {

        ResponseEntity<Object> document = documentService.uploadDocument(
                applicationId,
                docType,
                file
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(document);
    }

    @PreAuthorize("hasAnyRole('COMPANY', 'CASE_WORKER')")
    @GetMapping("/document/{id}")
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



    /*    // =========================
        // Update audit log
        // =========================

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode logArray;

        String currentLog = application.getAuditLog();

        try {

            if (currentLog.trim().isEmpty()
                    || "[]".equals(currentLog.trim())) {

                logArray = mapper.createArrayNode();

            } else {

                JsonNode parsed = mapper.readTree(currentLog);

                if (parsed != null && parsed.isArray()) {
                    logArray = (ArrayNode) parsed;
                } else {
                    logArray = mapper.createArrayNode();
                }
            }

        } catch (IOException e) {

            // Om befintlig audit log är trasig,
            // börja om med en tom array.
            logArray = mapper.createArrayNode();
        }

        // Skapa ny audit-log entry
        ObjectNode newEntry = mapper.createObjectNode();

        newEntry.put(
                "ts",
                LocalDateTime
                        .now(ZoneId.of("UTC"))
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        newEntry.put(
                "action",
                "DOCUMENT_UPLOADED"
        );

        newEntry.put(
                "filename",
                originalFilename
        );

        newEntry.put(
                "docType",
                docType
        );

        logArray.add(newEntry);

        // Spara audit log
        try {

            application.setAuditLog(
                    mapper.writeValueAsString(logArray)
            );

        } catch (JsonProcessingException e) {

            // Behåll gammal audit log om serialisering misslyckas.
        } */




}


