package se.comerit.resurs.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import se.comerit.resurs.entity.Decision;

@Schema(description = "Payload for submitting a manual decision on an application")
public record DecisionRequest(
        @Schema(description = "The decision to apply to the application", example = "APPROVED")
        @NotNull(message = "Decision is required")
        Decision decision,
        @Schema(description = "Free-text comment explaining the decision (max 500 characters)",
                example = "Financials verified; approving as requested.",
                maxLength = 500)
        @Size(max = 500, message = "Comment cannot exceed 500 characters")
        String comment
) {

}