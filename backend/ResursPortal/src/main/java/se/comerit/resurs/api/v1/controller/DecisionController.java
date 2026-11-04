package se.comerit.resurs.api.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import se.comerit.resurs.api.v1.dto.ApplicationResponse;
import se.comerit.resurs.api.v1.dto.DecisionRequest;
import se.comerit.resurs.api.v1.service.DecisionService;
import se.comerit.resurs.security.UserPrincipal;

@RestController
@RequestMapping("api/v1/applications")
@Tag(name = "Decisions", description = "Case worker decisions on credit applications")
@SecurityRequirement(name = "Session Cookie")
public class DecisionController {

    private final DecisionService service;

    public DecisionController(DecisionService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('CASE_WORKER')")
    @PostMapping("{id}/decision")
    @Operation(
        summary = "Decide an application",
        description = "Submit an approval or rejection decision for a credit application. "
                + "Requires the CASE_WORKER role.")
    @ApiResponse(responseCode = "200", description = "Decision applied; returns the updated application")
    @ApiResponse(responseCode = "400", description = "Validation error in the decision payload")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "Caller does not have the CASE_WORKER role")
    @ApiResponse(responseCode = "404", description = "Application not found")
    @ApiResponse(responseCode = "409", description = "Application has already been decided")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<ApplicationResponse> decide(
            @Parameter(description = "Unique application ID", example = "1")
            @PathVariable("id") Long id,
            @RequestBody @Valid DecisionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.decide(id, request, principal.asCaseWorker()));
    }
}