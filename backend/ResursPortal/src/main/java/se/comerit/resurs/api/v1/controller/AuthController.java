package se.comerit.resurs.api.v1.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import se.comerit.resurs.api.v1.dto.CaseWorkerLoginRequest;
import se.comerit.resurs.api.v1.dto.CompanyLoginRequest;
import se.comerit.resurs.api.v1.dto.RefreshRequest;
import se.comerit.resurs.api.v1.service.AuthService;
import se.comerit.resurs.security.AuthTokens;
import se.comerit.resurs.security.SessionFingerprint;
import se.comerit.resurs.security.UserPrincipal;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {
    private final AuthService service;
    private final SessionFingerprint fingerprint;

    public AuthController(AuthService service, SessionFingerprint fingerprint) {
        this.service = service;
        this.fingerprint = fingerprint;
    }

    @PostMapping("/login/company")
    public ResponseEntity<AuthTokens> loginCompany(@RequestBody CompanyLoginRequest body, HttpServletRequest req) {
        return ResponseEntity.ok(service.loginCompany(body.orgNumber(), fingerprint.of(req)));
    }

    @PostMapping("/login/caseWorker")
    public ResponseEntity<AuthTokens> loginCaseWorker(@RequestBody CaseWorkerLoginRequest body, HttpServletRequest req) {
        return ResponseEntity.ok(service.loginCaseWorker(body.email(), body.password(), fingerprint.of(req)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokens> refresh(@RequestBody RefreshRequest body, HttpServletRequest req) {
        return ResponseEntity.ok(service.refresh(body.refreshToken(), fingerprint.of(req)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        service.logout(principal);
        return ResponseEntity.noContent().build();
    }
}
