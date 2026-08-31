package se.comerit.resurs.api.v1.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import se.comerit.resurs.entity.ApplicationStatus;

public record ApplicationResponse(
    @NotNull 
    Long id,
    @NotBlank 
    String companyName,
    @NotBlank 
    String orgNumber,
    @NotNull 
    BigDecimal requestedAmount,
    @NotBlank 
    String purpose,
    @NotNull 
    ApplicationStatus status,
    String decision,
    String decisionReason,
    String scoringResult,
    @NotNull 
    LocalDateTime createdAt,
    @NotNull 
    LocalDateTime updatedAt
) {
    
}
