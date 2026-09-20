package se.comerit.resurs.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload for refreshing an access token")
public record RefreshRequest(
        @Schema(description = "Single-use refresh token issued at login or the previous refresh")
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {

}