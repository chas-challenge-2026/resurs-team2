package se.comerit.resurs.api.v1.controller;

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
public class CompanyController {

    @PreAuthorize("hasRole('COMPANY')")
    @GetMapping("/me")
    public ResponseEntity<CurrentCompanyResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        var company = principal.asCompany();
        return ResponseEntity.ok(new CurrentCompanyResponse(company.name(), company.orgNumber()));
    }
}
