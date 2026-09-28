package se.comerit.resurs.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credentials for case worker login")
public record CaseWorkerLoginRequest(
        @Schema(description = "Case worker email address",
                example = "worker@resurs.se",
                maxLength = 100)
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100)
        String email,
        @Schema(description = "Case worker password (8-128 characters)",
                example = "s3cure-password",
                minLength = 8,
                maxLength = 128)
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password
) {

}