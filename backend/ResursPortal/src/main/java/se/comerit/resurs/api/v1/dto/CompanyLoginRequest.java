package se.comerit.resurs.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyLoginRequest(
        @NotBlank(message = "Organization number is required")
        @Pattern(regexp = "^\\d{6}-\\d{4}$", message = "Organization number must be in format xxxxxx-xxxx")
        @Size(max = 20)
        String orgNumber
) {
    
}
