package se.comerit.resurs.api.v1.dto;

import java.util.UUID;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;

@Schema(description = "A document associated with a credit application")
public record DocumentResponse(
    @Schema(description = "Unique document identifier",
            example = "550e8400-e29b-41d4-a716-446655440000")
    @Nonnull
    UUID uuid,
    @Schema(description = "Original filename of the uploaded document",
            example = "annual-report-2025.pdf")
    @Nonnull
    String filename,
    @Schema(description = "Type or category of the document",
            example = "ANNUAL_REPORT")
    @Nonnull
    String docType,
    @Schema(description = "Timestamp when the document was uploaded")
    @Nonnull
    Instant uploadedAt
) {

}
