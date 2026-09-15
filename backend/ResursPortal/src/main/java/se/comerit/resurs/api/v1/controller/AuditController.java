package se.comerit.resurs.api.v1.controller;

import java.util.List;

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
public class AuditController {

    private final AuditLogService service;

    public AuditController(AuditLogService service) {
        this.service = service;
    }

    // TODO: Revisit whether company principals should have this endpoint.
    @PreAuthorize("hasAnyRole('COMPANY','CASE_WORKER')")
    @GetMapping("{id}/audit-log")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(@PathVariable("id") Long id,
            @RequestParam(value = "sort", defaultValue = "SEQUENCE_ASC") AuditSort sort,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.listAuditLogs(id, sort, principal));
    }
}