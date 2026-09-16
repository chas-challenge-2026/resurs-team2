package se.comerit.resurs.api.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import se.comerit.resurs.api.v1.dto.CurrentCompanyResponse;
import se.comerit.resurs.security.UserPrincipal;

@RestController
@RequestMapping("api/v1/companies")
@Tag(name = "Companies", description = "Information about the authenticated company")
@SecurityRequirement(name = "Bearer Authentication")
public class CompanyController {

    @PreAuthorize("hasRole('COMPANY')")
    @GetMapping("/me")
    @Operation(
        summary = "Get current company",
        description = "Return the name and organisation number of the authenticated company. "
                + "Requires the COMPANY role.")
    @ApiResponse(responseCode = "200", description = "Company details returned")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "Caller does not have the COMPANY role")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<CurrentCompanyResponse> me(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        var company = principal.asCompany();
        return ResponseEntity.ok(new CurrentCompanyResponse(company.name(), company.orgNumber()));
    }
}