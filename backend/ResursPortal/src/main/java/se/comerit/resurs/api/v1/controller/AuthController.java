package se.comerit.resurs.api.v1.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import se.comerit.resurs.api.dto.ApiError;
import se.comerit.resurs.api.v1.dto.CaseWorkerLoginRequest;
import se.comerit.resurs.api.v1.dto.CompanyLoginRequest;
import se.comerit.resurs.api.v1.dto.RefreshRequest;
import se.comerit.resurs.api.v1.service.AuthService;
import se.comerit.resurs.security.SessionFingerprint;
import se.comerit.resurs.security.UserPrincipal;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    private final SessionFingerprint fingerprint;

    public AuthController(AuthService service, SessionFingerprint fingerprint) {
        this.service = service;
        this.fingerprint = fingerprint;
    }

    @PostMapping("/login/company")
    public ResponseEntity<?> loginCompany(@RequestBody CompanyLoginRequest body, HttpServletRequest req) {
        return service.loginCompany(body.orgNumber(), fingerprint.of(req))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(this::unauthorized);
    }

    @PostMapping("/login/caseWorker")
    public ResponseEntity<?> loginCaseWorker(@RequestBody CaseWorkerLoginRequest body, HttpServletRequest req) {
        return service.loginCaseWorker(body.email(), body.password(), fingerprint.of(req))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(this::unauthorized);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest body, HttpServletRequest req) {
        return service.refresh(body.refreshToken(), fingerprint.of(req))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(this::unauthorized);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiError> logout(@AuthenticationPrincipal UserPrincipal principal) {
        service.logout(principal);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<ApiError> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(HttpStatus.UNAUTHORIZED.value(), "Invalid credentials"));
    }
}
