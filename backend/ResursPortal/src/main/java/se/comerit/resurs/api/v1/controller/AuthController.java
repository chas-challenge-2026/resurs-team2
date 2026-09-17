package se.comerit.resurs.api.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
@Tag(name = "Authentication", description = "Company and case worker login, token refresh, and logout")
public class AuthController {
    private final AuthService service;
    private final SessionFingerprint fingerprint;

    public AuthController(AuthService service, SessionFingerprint fingerprint) {
        this.service = service;
        this.fingerprint = fingerprint;
    }

    @PostMapping("/login/company")
    @Operation(
        summary = "Log in as a company",
        description = "Authenticate a company using its organisation number and receive "
                + "an access/refresh token pair.")
    @ApiResponse(responseCode = "200", description = "Login successful; tokens returned")
    @ApiResponse(responseCode = "400", description = "Validation error in the request body")
    @ApiResponse(responseCode = "401", description = "Invalid BankID authentication or unknown organisation number")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<AuthTokens> loginCompany(@Valid @RequestBody CompanyLoginRequest body, HttpServletRequest req) {
        return ResponseEntity.ok(service.loginCompany(body.orgNumber(), fingerprint.of(req)));
    }

    @PostMapping("/login/caseWorker")
    @Operation(
        summary = "Log in as a case worker",
        description = "Authenticate a case worker using email and password and receive "
                + "an access/refresh token pair.")
    @ApiResponse(responseCode = "200", description = "Login successful; tokens returned")
    @ApiResponse(responseCode = "400", description = "Validation error in the request body")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<AuthTokens> loginCaseWorker(@Valid @RequestBody CaseWorkerLoginRequest body, HttpServletRequest req) {
        return ResponseEntity.ok(service.loginCaseWorker(body.email(), body.password(), fingerprint.of(req)));
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh tokens",
        description = "Exchange a single-use refresh token for a new access/refresh token pair.")
    @ApiResponse(responseCode = "200", description = "New token pair returned")
    @ApiResponse(responseCode = "400", description = "Validation error in the request body")
    @ApiResponse(responseCode = "401", description = "Invalid, revoked, or expired refresh token")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<AuthTokens> refresh(@Valid @RequestBody RefreshRequest body, HttpServletRequest req) {
        return ResponseEntity.ok(service.refresh(body.refreshToken(), fingerprint.of(req)));
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Log out",
        description = "Invalidate the current session and revoke all tokens issued for it.",
        security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "Logged out successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "Caller does not have the COMPANY or CASE_WORKER role")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<Void> logout(@Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        service.logout(principal);
        return ResponseEntity.noContent().build();
    }
}