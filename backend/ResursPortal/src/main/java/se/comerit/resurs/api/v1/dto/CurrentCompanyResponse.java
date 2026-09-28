package se.comerit.resurs.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Details of the authenticated company")
public record CurrentCompanyResponse(
        @Schema(description = "Company name", example = "Acme AB")
        String companyName,
        @Schema(description = "Swedish organisation number", example = "556123-4567")
        String orgNumber) {

}