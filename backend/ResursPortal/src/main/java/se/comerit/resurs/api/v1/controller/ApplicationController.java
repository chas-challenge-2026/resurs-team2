package se.comerit.resurs.api.v1.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import se.comerit.resurs.api.v1.dto.CurrentCompanyResponse;
import se.comerit.resurs.security.UserPrincipal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("api/v1/application")
public class ApplicationController {

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
    public String submit(@RequestBody String entity) {
        // TODO: process POST request

        return entity;
    }

    @PreAuthorize("hasRole('COMPANY')")
    @GetMapping("/{id}")
    public String viewApplication(@RequestParam String param) {
        return new String();
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
