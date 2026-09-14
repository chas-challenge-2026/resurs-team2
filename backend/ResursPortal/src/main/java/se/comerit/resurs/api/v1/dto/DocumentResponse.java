package se.comerit.resurs.api.v1.dto;

import java.time.Instant;

import jakarta.annotation.Nonnull;

public record DocumentResponse(
    @Nonnull
    Long id,
    @Nonnull
    String filename,
    @Nonnull
    String docType,
    @Nonnull
    Instant uploadedAt
) {
    
}
