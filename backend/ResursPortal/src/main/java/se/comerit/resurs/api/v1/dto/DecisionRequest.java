package se.comerit.resurs.api.v1.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import se.comerit.resurs.entity.Decision;

public record DecisionRequest(
        @NotNull(message = "Application id is required")
        Long applicationId,
        @NotNull(message = "Decision is required")
        Decision decision,
        @Size(max = 500, message = "Comment cannot exceed 500 characters")
        String comment
) {

}
