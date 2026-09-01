package se.comerit.resurs.api.v1.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.dto.ApplicationOverview;
import se.comerit.resurs.api.v1.dto.ApplicationResponse;
import se.comerit.resurs.api.v1.dto.DecisionRequest;
import se.comerit.resurs.api.v1.service.BackofficeService;
import se.comerit.resurs.security.UserPrincipal;

@RestController
@RequestMapping("api/v1/backoffice")
@Validated
public class BackofficeController {

    private final BackofficeService service;

    public BackofficeController(BackofficeService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('CASE_WORKER')")
    @GetMapping
    public ResponseEntity<ApplicationOverview> overview(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        String caseWorker = principal.asCaseWorker().name();
        return ResponseEntity.ok(service.applicationOverview(caseWorker));
    }

    @PreAuthorize("hasRole('CASE_WORKER')")
    @PostMapping("decide")
    public ResponseEntity<ApplicationResponse> decide(@RequestBody @Valid DecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String caseWorker = principal.asCaseWorker().name();
        return ResponseEntity.ok(service.decide(request, caseWorker));
    }

    @PreAuthorize("hasRole('CASE_WORKER')")
    @GetMapping("application/{id}")
    public ResponseEntity<ApplicationDetailsResponse> viewApplicationDetails(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        String caseWorker = principal.asCaseWorker().name();
        return ResponseEntity.ok(service.viewApplicationDetails(id, caseWorker));
    }

}
