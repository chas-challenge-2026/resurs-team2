package se.comerit.resurs.api.v1.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import se.comerit.resurs.api.v1.dto.AuditLogResponse;
import se.comerit.resurs.api.v1.dto.AuditSort;
import se.comerit.resurs.api.v1.service.AuditLogService;
import se.comerit.resurs.security.UserPrincipal;

@RestController
@RequestMapping("api/v1/applications")
@Tag(name = "Audit Logs", description = "Audit trail for credit applications")
@SecurityRequirement(name = "Session Cookie")
public class AuditController {

    private final AuditLogService service;

    public AuditController(AuditLogService service) {
        this.service = service;
    }

    // TODO: Revisit whether company principals should have this endpoint.
    @PreAuthorize("hasAnyRole('COMPANY','CASE_WORKER')")
    @GetMapping("{id}/audit-log")
    @Operation(
        summary = "Get audit log for an application",
        description = "Return the audit trail of events for a credit application. "
                + "Companies may only access their own applications; case workers can "
                + "access any application.")
    @ApiResponse(responseCode = "200", description = "Audit log entries returned")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "Caller is not authorised to view this application")
    @ApiResponse(responseCode = "404", description = "Application not found")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(
            @Parameter(description = "Unique application ID", example = "1")
            @PathVariable("id") Long id,
            @Parameter(description = "Sort order for the returned entries",
                       schema = @Schema(implementation = AuditSort.class))
            @RequestParam(value = "sort", defaultValue = "SEQUENCE_ASC") AuditSort sort,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.listAuditLogs(id, sort, principal));
    }
}