package se.comerit.resurs.api.v1.dto;

import java.time.LocalDateTime;

import jakarta.annotation.Nonnull;

public record DocumentResponse(
    @Nonnull
    Long id,
    @Nonnull
    String filename,
    @Nonnull
    String docType,
    @Nonnull
    LocalDateTime uploadedAt
) {
    
}
