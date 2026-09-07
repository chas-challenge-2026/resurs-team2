package se.comerit.resurs.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigIntegrationTest {

    // Must match what SessionFingerprint.of() computes for these requests:
    // UA + "|" + remoteAddr ("127.0.0.1" default for MockMvc).
    private static final String UA = "test-agent";
    private static final String FP = UA + "|127.0.0.1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenStore store;

    private AuthTokens issueCompany() {
        return store.issue(
                new CompanyPrincipal(1L, "Malmö Fastigheter AB", "556000-1234"), FP);
    }

    // ---------- authorization on the /api chain ----------

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/test/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithValidTokenIs200() throws Exception {
        AuthTokens tokens = issueCompany();
        mockMvc.perform(get("/api/v1/test/ping")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void protectedEndpointWithInvalidTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/test/ping")
                        .header("Authorization", "Bearer definitely-not-a-token")
                        .header("User-Agent", UA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void errorBodyIsTypedJsonNotHtml() throws Exception {
        mockMvc.perform(get("/api/v1/test/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void companyRoleRejectedOnCaseWorkerOnlyRoute403() throws Exception {
        // /api/v1/backoffice/** requires ROLE_CASE_WORKER -> company gets 403.
        AuthTokens tokens = issueCompany();
        mockMvc.perform(get("/api/v1/backoffice/anything")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("User-Agent", UA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    void principalRoleExposedToController() throws Exception {
        AuthTokens tokens = issueCompany();
        mockMvc.perform(get("/api/v1/test/role")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(content().string("COMPANY"));
    }

    @Test
    void apiRequestDoesNotCreateHttpSession() throws Exception {
        AuthTokens tokens = issueCompany();
        var result = mockMvc.perform(get("/api/v1/test/ping")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    // ---------- caching policy on the API chain ----------

    @Test
    void apiResponsesAreNotCached() throws Exception {
        // Regression for MED-3: bearer tokens and any echoed credentials must never
        // be persisted by browsers/intermediaries — Spring Security's default cache
        // control header must include no-store, even on error responses.
        mockMvc.perform(get("/api/v1/test/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    // ---------- rotation / revocation through the real store ----------

    @Test
    void revokedTokenIsRejected() throws Exception {
        AuthTokens tokens = issueCompany();
        store.revoke(tokens.accessToken());

        mockMvc.perform(get("/api/v1/test/ping")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("User-Agent", UA))
                .andExpect(status().isUnauthorized());
    }

    // ---------- non-breaking legacy web chain ----------

    @Test
    void legacyLoginPageStillReachable() throws Exception {
        // Web chain is permitAll during the strangler-fig migration.
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void legacyRootRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
