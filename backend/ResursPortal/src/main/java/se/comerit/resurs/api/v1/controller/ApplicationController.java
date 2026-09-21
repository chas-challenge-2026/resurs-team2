package se.comerit.resurs.api.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.dto.ApplicationRequest;
import se.comerit.resurs.api.v1.dto.ApplicationResponse;
import se.comerit.resurs.api.v1.service.ApplicationService;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.security.UserPrincipal;

import java.util.List;

@RestController
@RequestMapping("api/v1/applications")
@Tag(name = "Applications", description = "Submit and manage credit applications")
@SecurityRequirement(name = "Bearer Authentication")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('COMPANY')")
    @PostMapping
    @Operation(
        summary = "Submit a credit application",
        description = "Create a new credit application for the authenticated company. "
                + "The company is identified from the bearer token. The requested amount "
                + "must be between 50 000 and 10 000 000 SEK.")
    @ApiResponse(responseCode = "200", description = "Application submitted successfully; returns the new application ID")
    @ApiResponse(responseCode = "400", description = "Validation error or company not registered")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "Caller does not have the COMPANY role")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<Long> submit(
            @Valid @RequestBody ApplicationRequest application,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        // NOTE: Currently requires company to be registered, the original would create
        // the company if missing
        // TODO: Consider what to do, maybe check if data matches database otherwise
        // register new company.
        var company = principal.asCompany();
        String orgNumber = company.orgNumber();
        return ResponseEntity.ok(service.submitApplication(orgNumber, application));
    }

    @PreAuthorize("hasAnyRole('COMPANY','CASE_WORKER')")
    @GetMapping("/{id}")
    @Operation(
        summary = "View application details",
        description = "Retrieve full details of a specific credit application, including "
                + "associated documents and financial data. Companies may only view their "
                + "own applications; case workers can view any application.")
    @ApiResponse(responseCode = "200", description = "Application details returned")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "Caller is not authorised to view this application")
    @ApiResponse(responseCode = "404", description = "Application not found")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<ApplicationDetailsResponse> viewApplication(
            @Parameter(description = "Unique application ID", example = "1")
            @PathVariable("id") Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.viewApplication(id, principal));
    }

    @PreAuthorize("hasAnyRole('COMPANY','CASE_WORKER')")
    @GetMapping
    @Operation(
        summary = "List applications",
        description = "List credit applications visible to the authenticated principal. "
                + "Companies see only their own applications; case workers see all applications. "
                + "An optional status filter narrows the results.")
    @ApiResponse(responseCode = "200", description = "List of applications returned")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "Caller does not have the COMPANY or CASE_WORKER role")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<List<ApplicationResponse>> listApplications(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Filter by application status",
                       schema = @Schema(implementation = ApplicationStatus.class))
            @RequestParam(required = false) ApplicationStatus status) {
        return ResponseEntity.ok(service.listApplications(principal, status));
    }
}
