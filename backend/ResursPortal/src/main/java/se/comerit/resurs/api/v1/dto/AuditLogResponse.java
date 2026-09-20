package se.comerit.resurs.api.v1.dto;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;

@Schema(description = "A single audit log entry for an application")
public record AuditLogResponse(
    @Schema(description = "Monotonically increasing sequence number of the entry", example = "1")
    long sequenceNumber,
    @Schema(description = "Timestamp when the entry was written")
    @Nonnull
    Instant timestamp,
    @Schema(description = "Structured audit event data as key/value pairs",
            example = "{\"event\": \"APPLICATION_SUBMITTED\", \"actor\": \"556123-4567\"}")
    @Nonnull
    Map<String, Object> entry
) {

}