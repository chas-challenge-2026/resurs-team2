package se.comerit.resurs.api.v1.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@Schema(description = "Detailed view of a credit application, including documents and financial data")
public record ApplicationDetailsResponse(
    @Schema(description = "Core application data")
    @Nonnull
    ApplicationResponse application,
    @Schema(description = "Name of the assigned case worker, if any",
            example = "Anna Svensson",
            nullable = true)
    @Nullable
    String workerName,
    @Schema(description = "Documents uploaded for this application")
    @Nonnull
    List<DocumentResponse> documents,
    @Schema(description = "Financial data summary",
            nullable = true)
    @Nullable
    String financialData
) {

}
