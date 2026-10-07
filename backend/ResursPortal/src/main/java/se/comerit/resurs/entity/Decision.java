package se.comerit.resurs.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Decision outcome for a credit application")
public enum Decision {
    @Schema(description = "Application approved")
    APPROVED,
    @Schema(description = "Application rejected")
    REJECTED,
    @Schema(description = "Application needs additional documents")
    DOCUMENTS_NEEDED
}
