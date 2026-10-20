package se.comerit.resurs.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Credentials for company login")
public record CompanyLoginRequest(
        @Schema(description = "Company organisation number in the format xxxxxx-xxxx",
                example = "556123-4567")
        @NotBlank(message = "Organization number is required")
        @Pattern(regexp = "^\\d{6}-\\d{4}$", message = "Organization number must be in format xxxxxx-xxxx")
        @Size(max = 20)
        String orgNumber
) {

}