package se.comerit.resurs.api.v1.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApplicationDetailsResponse(
    @NotNull
    ApplicationResponse application,
    @NotBlank
    String auditLogRaw,
    @NotBlank 
    String workerName,
    @NotNull 
    List<DocumentResponse> documents
) {
    
}
