package se.comerit.resurs.api.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import se.comerit.resurs.api.v1.dto.CaseWorkerLoginRequest;
import se.comerit.resurs.api.v1.dto.CompanyLoginRequest;
import se.comerit.resurs.api.v1.dto.PrincipalResponse;
import se.comerit.resurs.api.v1.service.AuthService;
import se.comerit.resurs.security.AuthTokens;
import se.comerit.resurs.security.SessionCookie;
import se.comerit.resurs.security.SessionFingerprint;
import se.comerit.resurs.security.UserPrincipal;

/**
 * Authentication endpoints. Sessions are carried exclusively in httpOnly
 * cookies ({@link SessionCookie}): login and refresh {@code Set-Cookie} the
 * access/refresh pair and the responses carry only the principal. Logout
 * revokes the session and clears the cookies. The filter takes care of
 * transparent rotation for regular API requests; {@code POST /refresh} exists
 * for explicit/curl-style rotation using the refresh cookie.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Validated
@Tag(name = "Authentication", description = "Company and case worker login, token refresh, session info, and logout")
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
        description = "Authenticate a company using its organisation number. On success the "
                + "session is set as httpOnly cookies and the principal is returned.")
    @ApiResponse(responseCode = "200", description = "Login successful; session cookies set")
    @ApiResponse(responseCode = "400", description = "Validation error in the request body")
    @ApiResponse(responseCode = "401", description = "Invalid BankID authentication or unknown organisation number")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<PrincipalResponse> loginCompany(@Valid @RequestBody CompanyLoginRequest body,
            HttpServletRequest req, HttpServletResponse res) {
        AuthTokens tokens = service.loginCompany(body.orgNumber(), fingerprint.of(req));
        res.addCookie(SessionCookie.access(tokens.accessToken()));
        res.addCookie(SessionCookie.refresh(tokens.refreshToken()));
        return ResponseEntity.ok(PrincipalResponse.from(tokens));
    }

    @PostMapping("/login/caseWorker")
    @Operation(
        summary = "Log in as a case worker",
        description = "Authenticate a case worker using email and password. On success the "
                + "session is set as httpOnly cookies and the principal is returned.")
    @ApiResponse(responseCode = "200", description = "Login successful; session cookies set")
    @ApiResponse(responseCode = "400", description = "Validation error in the request body")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<PrincipalResponse> loginCaseWorker(@Valid @RequestBody CaseWorkerLoginRequest body,
            HttpServletRequest req, HttpServletResponse res) {
        AuthTokens tokens = service.loginCaseWorker(body.email(), body.password(), fingerprint.of(req));
        res.addCookie(SessionCookie.access(tokens.accessToken()));
        res.addCookie(SessionCookie.refresh(tokens.refreshToken()));
        return ResponseEntity.ok(PrincipalResponse.from(tokens));
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh tokens",
        description = "Exchange the single-use refresh cookie for a new access/refresh pair, set "
                + "as new httpOnly cookies, and return the principal.")
    @ApiResponse(responseCode = "200", description = "New session cookies set")
    @ApiResponse(responseCode = "401", description = "Missing, invalid, revoked, or expired refresh cookie")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    public ResponseEntity<PrincipalResponse> refresh(
            @CookieValue(name = SessionCookie.REFRESH, required = false) String refreshToken,
            HttpServletRequest req, HttpServletResponse res) {
        AuthTokens tokens = service.refresh(refreshToken, fingerprint.of(req));
        res.addCookie(SessionCookie.access(tokens.accessToken()));
        res.addCookie(SessionCookie.refresh(tokens.refreshToken()));
        return ResponseEntity.ok(PrincipalResponse.from(tokens));
    }

    @GetMapping("/me")
    @Operation(
        summary = "Current principal",
        description = "Returns the principal of the current session. The session filter rotates "
                + "the pair transparently when the access cookie is stale but a valid refresh "
                + "cookie is present, so this endpoint doubles as the reload-restore call.")
    @ApiResponse(responseCode = "200", description = "Current principal")
    @ApiResponse(responseCode = "401", description = "No valid session")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    @SecurityRequirement(name = "Session Cookie")
    public ResponseEntity<PrincipalResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(PrincipalResponse.from(principal));
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Log out",
        description = "Invalidate the current session (the session that presented the access "
                + "cookie) and clear both session cookies.")
    @ApiResponse(responseCode = "204", description = "Logged out successfully; cookies cleared")
    @ApiResponse(responseCode = "401", description = "Missing or invalid session")
    @ApiResponse(responseCode = "403", description = "Caller does not have the COMPANY or CASE_WORKER role")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    @SecurityRequirement(name = "Session Cookie")
    public ResponseEntity<Void> logout(
            @CookieValue(name = SessionCookie.ACCESS, required = false) String accessToken,
            HttpServletResponse res) {
        if (accessToken != null) {
            service.logout(accessToken);
        }
        res.addCookie(SessionCookie.clearAccess());
        res.addCookie(SessionCookie.clearRefresh());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout/all")
    @Operation(
        summary = "Log out everywhere",
        description = "Revoke every active session for the authenticated user (current device and "
                + "all other devices/browsers) and clear the session cookies. Use to clear stale "
                + "logins, e.g. after a suspected compromise.")
    @ApiResponse(responseCode = "204", description = "All sessions revoked; cookies cleared")
    @ApiResponse(responseCode = "401", description = "Missing or invalid session")
    @ApiResponse(responseCode = "403", description = "Caller does not have the COMPANY or CASE_WORKER role")
    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
    @SecurityRequirement(name = "Session Cookie")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal UserPrincipal principal,
            HttpServletResponse res) {
        service.logoutAll(principal);
        res.addCookie(SessionCookie.clearAccess());
        res.addCookie(SessionCookie.clearRefresh());
        return ResponseEntity.noContent().build();
    }
}