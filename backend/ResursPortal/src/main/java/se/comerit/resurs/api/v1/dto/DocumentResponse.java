package se.comerit.resurs.api.v1.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import java.time.Instant;

import jakarta.annotation.Nonnull;

public record DocumentResponse(
    @Nonnull
    UUID uuid,
    @Nonnull
    String filename,
    @Nonnull
    String docType,
    @Nonnull
    Instant uploadedAt
) {
    
}
