package se.comerit.resurs.api.v1.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.dto.ApplicationOverview;
import se.comerit.resurs.api.v1.dto.ApplicationResponse;
import se.comerit.resurs.api.v1.dto.DecisionRequest;
import se.comerit.resurs.api.v1.service.BackofficeService;
import se.comerit.resurs.security.UserPrincipal;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("api/v1/backoffice")
public class BackofficeController {

    private final BackofficeService service;

    public BackofficeController(BackofficeService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApplicationOverview> overview(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        String caseWorker = principal.asCaseWorker().name();
        return ResponseEntity.ok(service.applicationOverview(caseWorker));
    }

    @PostMapping("decide")
    public ResponseEntity<ApplicationResponse> decide(@RequestBody @Valid DecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String caseWorker = principal.asCaseWorker().name();
        return ResponseEntity.ok(service.decide(request, caseWorker));
    }

    @GetMapping("application/{id}")
    public ResponseEntity<ApplicationDetailsResponse> viewApplicationDetails(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        String caseWorker = principal.asCaseWorker().name();
        return ResponseEntity.ok(service.viewApplicationDetails(id, caseWorker));
    }

}
