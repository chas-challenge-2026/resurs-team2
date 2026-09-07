package se.comerit.resurs.api.v1.controller;

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
public class DecisionController {

    private final DecisionService service;

    public DecisionController(DecisionService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('CASE_WORKER')")
    @PostMapping("{id}/decision")
    public ResponseEntity<ApplicationResponse> decide(@PathVariable("id") Long id,
            @RequestBody @Valid DecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String caseWorker = principal.asCaseWorker().name();
        return ResponseEntity.ok(service.decide(id, request, caseWorker));
    }
}