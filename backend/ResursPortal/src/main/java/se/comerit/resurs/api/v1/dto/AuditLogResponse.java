package se.comerit.resurs.api.v1.dto;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.annotation.Nonnull;

public record AuditLogResponse(
    long sequenceNumber,
    @Nonnull
    LocalDateTime timestamp,
    @Nonnull
    Map<String, Object> entry
) {
    
}