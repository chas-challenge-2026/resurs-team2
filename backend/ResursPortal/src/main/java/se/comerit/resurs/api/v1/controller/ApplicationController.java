package se.comerit.resurs.api.v1.controller;

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

import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.dto.ApplicationRequest;
import se.comerit.resurs.api.v1.dto.CurrentCompanyResponse;
import se.comerit.resurs.api.v1.service.ApplicationService;
import se.comerit.resurs.security.UserPrincipal;

@RestController
@RequestMapping("api/v1/application")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('COMPANY')")
    @GetMapping("apply")
    public ResponseEntity<CurrentCompanyResponse> showApplyForm(@AuthenticationPrincipal UserPrincipal principal) {
        var company = principal.asCompany();
        String orgNumber = company.orgNumber();
        String name = company.name();
        return ResponseEntity.ok(new CurrentCompanyResponse(name, orgNumber));
    }

    @PreAuthorize("hasRole('COMPANY')")
    @PostMapping("apply")
    public ResponseEntity<Long> submit(@RequestBody ApplicationRequest application,
            @AuthenticationPrincipal UserPrincipal principal) {
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
    public ResponseEntity<ApplicationDetailsResponse> viewApplication(@PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.viewApplication(id, principal));
    }

    @PreAuthorize("hasRole('COMPANY')")
    @GetMapping
    public String listApplications(@RequestParam String param) {
        return new String();
    }

    @PreAuthorize("hasRole('COMPANY')")
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam String param) {
        return new String();
    }

}
