package se.comerit.resurs.api.v1.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import se.comerit.resurs.entity.ApplicationStatus;

@Schema(description = "Summary representation of a credit application")
public record ApplicationResponse(
    @Schema(description = "Unique application identifier", example = "1")
    @Nonnull
    Long id,
    @Schema(description = "Name of the applicant company", example = "Acme AB")
    @Nonnull
    String companyName,
    @Schema(description = "Swedish organisation number", example = "5561234567")
    @Nonnull
    String orgNumber,
    @Schema(description = "Requested loan amount in SEK", example = "7500000")
    @Nonnull
    BigDecimal requestedAmount,
    @Schema(description = "Intended use of the loan funds", example = "Working capital for expansion")
    @Nonnull
    String purpose,
    @Schema(description = "Current processing status of the application")
    @Nonnull
    ApplicationStatus status,
    @Schema(description = "Decision outcome if the application has been decided",
            example = "APPROVED",
            nullable = true)
    @Nullable
    String decision,
    @Schema(description = "Reason for the decision, if provided",
            example = "Strong financials",
            nullable = true)
    @Nullable
    String decisionReason,
    @Schema(description = "Automated scoring result summary",
            example = "APPROVED(score=85)",
            nullable = true)
    @Nullable
    String scoringResult,
    @Schema(description = "Timestamp when the application was created")
    @Nonnull
    Instant createdAt,
    @Schema(description = "Timestamp when the application was last updated")
    @Nonnull
    Instant updatedAt
) {

}
