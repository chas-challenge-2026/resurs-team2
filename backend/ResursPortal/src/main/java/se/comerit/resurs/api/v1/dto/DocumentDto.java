package se.comerit.resurs.api.v1.dto;

import se.comerit.resurs.entity.Document;

import java.time.Instant;

/**
 * Document DTO.
 * <p>
 * {@code filename} is the user-facing original file name (e.g. "annual-report.pdf").
 * The opaque storage key ({@code <uuid>.pdf}) stays internal to the service layer
 * and is never exposed to clients.
 */
public record DocumentDto(
        java.util.UUID uuid,
        Long applicationId,
        String filename,
        String docType,
        Instant uploadedAt
) {
    public static DocumentDto from(Document document) {
        return new DocumentDto(
                document.getUuid(),
                document.getApplication().getId(),
                document.getOriginalFilename(),
                document.getDocType(),
                document.getUploadedAt()
        );
    }
}