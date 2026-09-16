package se.comerit.resurs.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Processing status of a credit application")
public enum ApplicationStatus {
    @Schema(description = "Application submitted, awaiting required documents")
    PENDING_DOCS,
    @Schema(description = "Documents received, under review by a case worker")
    UNDER_REVIEW,
    @Schema(description = "Application approved")
    APPROVED,
    @Schema(description = "Application rejected")
    REJECTED
}
