package se.comerit.resurs.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Supported sort orders for the application audit log endpoint.
 */
@Schema(description = "Sort order for audit log entries")
public enum AuditSort {
    @Schema(description = "Sort by sequence number, oldest first")
    SEQUENCE_ASC,
    @Schema(description = "Sort by sequence number, newest first")
    SEQUENCE_DESC,
    @Schema(description = "Sort by timestamp, oldest first")
    TIMESTAMP_ASC,
    @Schema(description = "Sort by timestamp, newest first")
    TIMESTAMP_DESC
}