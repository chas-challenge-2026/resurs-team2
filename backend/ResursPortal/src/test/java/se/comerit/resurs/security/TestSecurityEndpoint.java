package se.comerit.resurs.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoint used to drive the /api/** security chain in MockMvc
 * integration tests. It lives in src/test so it never ships, and it is
 * intentionally protected so the tests can assert 401 / role enforcement.
 *
 * It is a stand-in for the real /api/v1 feature controllers (which don't exist
 * yet); remove it once those are implemented and covered by tests.
 */
@RestController
@RequestMapping("/api/v1/test")
public class TestSecurityEndpoint {

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    @GetMapping("/role")
    public String role(@AuthenticationPrincipal UserPrincipal principal) {
        return principal.role().name();
    }
}
