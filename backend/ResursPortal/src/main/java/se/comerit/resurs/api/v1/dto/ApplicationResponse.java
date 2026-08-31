package se.comerit.resurs.api.v1.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import se.comerit.resurs.entity.ApplicationStatus;

public record ApplicationResponse(
    @Nonnull
    Long id,
    @Nonnull
    String companyName,
    @Nonnull
    String orgNumber,
    @Nonnull
    BigDecimal requestedAmount,
    @Nonnull
    String purpose,
    @Nonnull
    ApplicationStatus status,
    @Nullable 
    String decision,
    @Nullable 
    String decisionReason,
    @Nullable 
    String scoringResult,
    @Nonnull
    LocalDateTime createdAt,
    @Nonnull
    LocalDateTime updatedAt
) {
    
}
