package se.comerit.resurs.api.v1.dto;

import se.comerit.resurs.entity.Document;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Metadata for a document uploaded to an application")
/**
 * Document DTO.
 * <p>
 * {@code filename} is the user-facing original file name (e.g. "annual-report.pdf").
 * The opaque storage key ({@code <uuid>.pdf}) stays internal to the service layer
 * and is never exposed to clients.
 */
public record DocumentDto(
        @Schema(description = "Unique document identifier",
                example = "550e8400-e29b-41d4-a716-446655440000")
        java.util.UUID uuid,
        @Schema(description = "Identifier of the application the document belongs to", example = "1")
        Long applicationId,
        @Schema(description = "Original filename of the uploaded document", example = "annual-report-2025.pdf")
        String filename,
        @Schema(description = "Type or category of the document", example = "ANNUAL_REPORT")
        String docType,
        @Schema(description = "Timestamp when the document was uploaded")
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