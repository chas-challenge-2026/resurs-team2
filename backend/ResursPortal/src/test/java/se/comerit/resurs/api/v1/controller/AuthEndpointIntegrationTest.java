package se.comerit.resurs.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import se.comerit.resurs.repository.CaseWorkerRepository;
import se.comerit.resurs.security.AuthTokens;
import se.comerit.resurs.security.PrincipalRole;
import se.comerit.resurs.security.SessionCookie;

import org.springframework.test.context.jdbc.Sql;

/**
 * Endpoint-level validation of the {@code /api/v1/auth} flows through the real
 * HTTP API, with sessions carried in httpOnly cookies instead of bearer
 * headers: login/refresh set cookies, rotation and single-use replay,
 * transparent filter-side rotation (which doubles as the reload-restore path
 * via {@code GET /me}), logout scoping and theft detection. Each test
 * exercises the full request path including the security filter and the
 * session-token fingerprint binding.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM documents",
        "DELETE FROM applications",
        "DELETE FROM companies",
        "DELETE FROM case_workers",
        "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (900, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Anders Karlsson')",
        "INSERT INTO case_workers (id, name, email, email_index, password) VALUES (900, 'Karin Handläggare', 'karin@resurs.se', X'240cf76b4caf0123ebfc7392cd379b0467f4026fa356c0a05bfa360a87679413', '$2a$10$rUonBwDLz9IA0Ivwnor38.tjZevxSeIHzQx5b4u0RwHhHJ/sbao32')"
})
class AuthEndpointIntegrationTest {

    private static final String UA = "auth-test-agent";
    private static final String OTHER_UA = "stolen-device-agent";

    private static final String COMPANY_ORG = "556000-1234";

    private static final String CASE_WORKER_EMAIL = "karin@resurs.se";
    private static final String CASE_WORKER_PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Argon2PasswordEncoder argon2;

    @Autowired
    private CaseWorkerRepository caseWorkerRepository;

    /**
     * The shared seed stores a placeholder hash (mirrors the other integrated
     * tests); re-encode with the real Argon2 encoder so the seeded case worker
     * can actually log in. Runs after the {@code @Sql} scripts (which execute in
     * the {@code beforeTestMethod} phase) and applies to every nested test too.
     */
    @BeforeEach
    void ensureCaseWorkerPasswordIsArgon2() {
        caseWorkerRepository.findByEmail(CASE_WORKER_EMAIL).ifPresent(cw -> {
            cw.setPassword(argon2.encode(CASE_WORKER_PASSWORD));
            caseWorkerRepository.save(cw);
        });
    }

    @Nested
    @DisplayName("Token rotation")
    class Rotation {

        @Test
        @DisplayName("Refresh issues a fresh pair, retires the old pair, and is single-use")
        void refreshRotatesAndIsSingleUse() throws Exception {
            AuthTokens original = loginCompany();

            // Presenting the refresh cookie yields a brand-new pair.
            MvcResult rotatedResult = mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.refresh(original.refreshToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("COMPANY"))
                    .andReturn();
            AuthTokens rotated = readTokens(rotatedResult);

            // New pair differs from the original tokens.
            assertThat(rotated.accessToken()).isNotEqualTo(original.accessToken());
            assertThat(rotated.refreshToken()).isNotEqualTo(original.refreshToken());

            // The rotated (new) access cookie is valid.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(rotated.accessToken())))
                    .andExpect(status().isOk());

            // The old access cookie is dead after rotation.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(original.accessToken())))
                    .andExpect(status().isUnauthorized());

            // The old refresh cookie is single-use: a replay is rejected.
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.refresh(original.refreshToken())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @DisplayName("Refresh with a garbage/unknown cookie is rejected")
        void refreshWithUnknownTokenRejected() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.refresh("never-issued-token")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @DisplayName("Refresh without a refresh cookie is rejected")
        void refreshWithoutRefreshCookieRejected() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @DisplayName("Concurrent refresh with the same single-use cookie: exactly one wins, session survives")
        void concurrentRefreshCookieUsedOnlyOnce() throws Exception {
            AuthTokens original = loginCompany();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<MvcResult>> futures = IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/v1/auth/refresh")
                                    .header("User-Agent", UA)
                                    .cookie(SessionCookie.refresh(original.refreshToken())))
                            .andReturn();
                })).toList();

                start.countDown();
                AuthTokens rotated = null;
                int ok = 0;
                int unauthorized = 0;
                for (Future<MvcResult> future : futures) {
                    MvcResult result = future.get(10, TimeUnit.SECONDS);
                    if (result.getResponse().getStatus() == 200) {
                        ok++;
                        rotated = readTokens(result);
                    } else if (result.getResponse().getStatus() == 401) {
                        unauthorized++;
                    }
                }
                assertThat(ok).isEqualTo(1);
                assertThat(unauthorized).isEqualTo(1);

                // The losing request does not destroy the session: the winner's
                // fresh pair is fully usable (this mirrors the browser retrying
                // with the rotated cookies).
                assertThat(rotated).isNotNull();
                mockMvc.perform(get("/api/v1/companies/me")
                                .header("User-Agent", UA)
                                .cookie(SessionCookie.access(rotated.accessToken())))
                        .andExpect(status().isOk());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("Session restore (GET /me)")
    class SessionRestore {

        @Test
        @DisplayName("/me returns the principal from a valid access cookie")
        void meReturnsCurrentPrincipal() throws Exception {
            AuthTokens tokens = loginCompany();

            mockMvc.perform(get("/api/v1/auth/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(tokens.accessToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("COMPANY"))
                    .andExpect(jsonPath("$.name").value("Malmö Fastigheter AB"));
        }

        @Test
        @DisplayName("/me without any session is 401")
        void meWithoutSessionIs401() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("User-Agent", UA))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @DisplayName("Reload restore: a stale access cookie combined with a valid refresh cookie transparently rotates")
        void meRotatesTransparentlyWhenAccessCookieMissing() throws Exception {
            AuthTokens original = loginCompany();

            // Present only the refresh cookie — as after a reload where the
            // access cookie was lost/stale. The filter must rotate transparently,
            // set fresh cookies, and serve /me.
            MvcResult me = mockMvc.perform(get("/api/v1/auth/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.refresh(original.refreshToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("COMPANY"))
                    .andReturn();
            AuthTokens rotated = readTokens(me);
            assertThat(rotated.accessToken()).isNotEqualTo(original.accessToken());

            // The freshly rotated access cookie works.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(rotated.accessToken())))
                    .andExpect(status().isOk());

            // The refresh cookie is single-use: replaying it on /me is rejected.
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.refresh(original.refreshToken())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Case worker role")
    class CaseWorker {

        @Test
        @DisplayName("Case worker login and /me serve the canonical CASEWORKER role")
        void caseWorkerLoginServesCanonicalRole() throws Exception {
            MvcResult login = mockMvc.perform(post("/api/v1/auth/login/caseWorker")
                            .header("User-Agent", UA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + CASE_WORKER_EMAIL
                                    + "\",\"password\":\"" + CASE_WORKER_PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("CASEWORKER"))
                    .andExpect(jsonPath("$.name").value("Karin Handläggare"))
                    .andReturn();
            AuthTokens tokens = readTokens(login);

            mockMvc.perform(get("/api/v1/auth/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(tokens.accessToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("CASEWORKER"))
                    .andExpect(jsonPath("$.name").value("Karin Handläggare"));
        }
    }

    @Nested
    @DisplayName("Logout invalidation")
    class Logout {

        @Test
        @DisplayName("After logout neither the access nor refresh cookie works")
        void logoutRevokesAccessAndRefresh() throws Exception {
            AuthTokens tokens = loginCompany();

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(tokens.accessToken())))
                    .andExpect(status().isNoContent());

            // The logged-out access cookie can no longer reach a protected endpoint.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(tokens.accessToken())))
                    .andExpect(status().isUnauthorized());

            // The logged-out refresh cookie can no longer be used to rotate.
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.refresh(tokens.refreshToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Logout on one browser must not invalidate a second active session for the same user")
        void logoutDoesNotKillOtherActiveSessions() throws Exception {
            // Simulate two browsers logging in simultaneously with different fingerprints.
            String browserA = "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0";
            String browserB = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/131.0";

            AuthTokens sessionA = loginCompanyWithUA(browserA);
            AuthTokens sessionB = loginCompanyWithUA(browserB);

            // Both sessions are valid initially.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", browserA)
                            .cookie(SessionCookie.access(sessionA.accessToken())))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", browserB)
                            .cookie(SessionCookie.access(sessionB.accessToken())))
                    .andExpect(status().isOk());

            // Browser A logs out.
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("User-Agent", browserA)
                            .cookie(SessionCookie.access(sessionA.accessToken())))
                    .andExpect(status().isNoContent());

            // Session A is revoked — access and refresh both dead.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", browserA)
                            .cookie(SessionCookie.access(sessionA.accessToken())))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", browserA)
                            .cookie(SessionCookie.refresh(sessionA.refreshToken())))
                    .andExpect(status().isUnauthorized());

            // Session B must still be active — the session-scoped logout only revokes
            // the session that presented the cookie, leaving all other sessions of the
            // same user untouched.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", browserB)
                            .cookie(SessionCookie.access(sessionB.accessToken())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Logout must not invalidate a second session with the SAME fingerprint (normal + incognito window)")
        void logoutDoesNotKillSessionsWithIdenticalFingerprint() throws Exception {
            // Regression: a normal browser and an incognito window of the same
            // browser share the User-Agent (and IP), so both sessions carry the
            // IDENTICAL fingerprint. Logging out in one window must still only
            // revoke that window's session — not the other one.
            AuthTokens sessionA = loginCompanyWithUA(UA);
            AuthTokens sessionB = loginCompanyWithUA(UA);

            // Both sessions are valid.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(sessionA.accessToken())))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(sessionB.accessToken())))
                    .andExpect(status().isOk());

            // The incognito window logs out.
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(sessionA.accessToken())))
                    .andExpect(status().isNoContent());

            // Session A is dead — access and refresh.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(sessionA.accessToken())))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.refresh(sessionA.refreshToken())))
                    .andExpect(status().isUnauthorized());

            // The normal window's session B is untouched — access AND refresh.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(sessionB.accessToken())))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.refresh(sessionB.refreshToken())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Log out everywhere revokes every active session of the user")
        void logoutAllRevokesEverySessionOfTheUser() throws Exception {
            String browserA = "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0";
            String browserB = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/131.0";

            AuthTokens sessionA = loginCompanyWithUA(browserA);
            AuthTokens sessionB = loginCompanyWithUA(browserB);

            // Both sessions are valid initially.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", browserA)
                            .cookie(SessionCookie.access(sessionA.accessToken())))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", browserB)
                            .cookie(SessionCookie.access(sessionB.accessToken())))
                    .andExpect(status().isOk());

            // Browser A asks to log out EVERYWHERE.
            mockMvc.perform(post("/api/v1/auth/logout/all")
                            .header("User-Agent", browserA)
                            .cookie(SessionCookie.access(sessionA.accessToken())))
                    .andExpect(status().isNoContent());

            // Session A is dead — access and refresh.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", browserA)
                            .cookie(SessionCookie.access(sessionA.accessToken())))
                    .andExpect(status().isUnauthorized());

            // And session B is dead too — unlike the session-scoped /auth/logout,
            // /auth/logout/all wipes every session of the principal.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", browserB)
                            .cookie(SessionCookie.access(sessionB.accessToken())))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", browserB)
                            .cookie(SessionCookie.refresh(sessionB.refreshToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Log out everywhere requires an authenticated caller")
        void logoutAllRequiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout/all")
                            .header("User-Agent", UA))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Stolen token detection (fingerprint mismatch)")
    class StolenToken {

        @Test
        @DisplayName("A token presented from a different device (User-Agent) is rejected")
        void wrongFingerprintAccessRejected() throws Exception {
            AuthTokens tokens = loginCompany();

            // Legit device works.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(tokens.accessToken())))
                    .andExpect(status().isOk());

            // The same token from another device is treated as theft and rejected.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", OTHER_UA)
                            .cookie(SessionCookie.access(tokens.accessToken())))
                    .andExpect(status().isUnauthorized());

            // Theft tripwire nukes the whole session: the legit device is dead too.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(tokens.accessToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("A refresh attempted from a different device is rejected and revokes the session")
        void wrongFingerprintRefreshRejected() throws Exception {
            AuthTokens tokens = loginCompany();

            // Attacker replays the refresh cookie from another device.
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", OTHER_UA)
                            .cookie(SessionCookie.refresh(tokens.refreshToken())))
                    .andExpect(status().isUnauthorized());

            // Whole session revoked: legit access cookie no longer works, and the
            // refresh cookie is dead even from the correct device.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.access(tokens.accessToken())))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .cookie(SessionCookie.refresh(tokens.refreshToken())))
                    .andExpect(status().isUnauthorized());
        }
    }

    private AuthTokens loginCompany() throws Exception {
        return loginCompanyWithUA(UA);
    }

    private AuthTokens loginCompanyWithUA(String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/company")
                        .header("User-Agent", userAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNumber\":\"" + COMPANY_ORG + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return readTokens(result);
    }

    private AuthTokens readTokens(MvcResult result) throws java.io.IOException {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthTokens(
                cookieValue(result.getResponse(), SessionCookie.ACCESS),
                cookieValue(result.getResponse(), SessionCookie.REFRESH),
                roleFrom(body.get("role").asText()),
                body.get("name").asText());
    }

    private static String cookieValue(MockHttpServletResponse response, String name) {
        for (Cookie cookie : response.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        throw new AssertionError("Expected cookie '" + name + "' in the response");
    }

    private static PrincipalRole roleFrom(String canonical) {
        return "CASEWORKER".equals(canonical) ? PrincipalRole.CASE_WORKER : PrincipalRole.valueOf(canonical);
    }
}