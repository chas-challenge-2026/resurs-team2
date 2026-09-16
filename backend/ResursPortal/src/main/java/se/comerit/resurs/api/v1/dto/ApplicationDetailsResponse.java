package se.comerit.resurs.api.v1.dto;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public record ApplicationDetailsResponse(
    @Nonnull
    ApplicationResponse application,
    @Nullable
    String workerName,
    @Nonnull
    List<DocumentResponse> documents,
    @Nullable
    String financialData
) {
    
}
