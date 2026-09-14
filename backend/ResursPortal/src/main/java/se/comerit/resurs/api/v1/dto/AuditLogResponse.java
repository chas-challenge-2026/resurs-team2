package se.comerit.resurs.api.v1.dto;

import java.time.Instant;
import java.util.Map;

import jakarta.annotation.Nonnull;

public record AuditLogResponse(
    long sequenceNumber,
    @Nonnull
    Instant timestamp,
    @Nonnull
    Map<String, Object> entry
) {
    
}