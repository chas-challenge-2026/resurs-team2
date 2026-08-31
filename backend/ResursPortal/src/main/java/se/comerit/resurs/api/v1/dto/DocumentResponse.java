package se.comerit.resurs.api.v1.dto;

import java.time.LocalDateTime;

import io.micrometer.common.lang.NonNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentResponse(
    @NotNull
    Long id,
    @NotBlank 
    String filename,
    @NotBlank 
    String docType,
    @NonNull 
    LocalDateTime uploadedAt
) {
    
}
