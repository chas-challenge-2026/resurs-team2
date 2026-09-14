package se.comerit.resurs.api.v1.dto;

/**
 * Supported sort orders for the application audit log endpoint.
 */
public enum AuditSort {
    SEQUENCE_ASC,
    SEQUENCE_DESC,
    TIMESTAMP_ASC,
    TIMESTAMP_DESC
}