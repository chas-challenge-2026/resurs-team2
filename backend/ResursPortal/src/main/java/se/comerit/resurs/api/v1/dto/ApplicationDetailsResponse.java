package se.comerit.resurs.api.v1.dto;

import java.util.List;

import jakarta.annotation.Nonnull;

public record ApplicationDetailsResponse(
    @Nonnull
    ApplicationResponse application,
    @Nonnull
    String auditLogRaw,
    @Nonnull
    String workerName,
    @Nonnull
    List<DocumentResponse> documents
) {
    
}
