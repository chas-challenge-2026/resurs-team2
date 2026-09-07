package se.comerit.resurs.api.v1.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import se.comerit.resurs.security.AuthTokens;

/**
 * Endpoint-level validation of the {@code /api/v1/auth} flows through the real
 * HTTP API (session tokens, rotation, logout and theft detection). Each test
 * exercises the full request path including the security filter and the
 * session-token fingerprint binding.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthEndpointIntegrationTest {

    private static final String UA = "auth-test-agent";
    private static final String OTHER_UA = "stolen-device-agent";

    private static final String COMPANY_ORG = "556000-1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("Token rotation")
    class Rotation {

        @Test
        @DisplayName("Refresh issues a fresh pair, retires the old pair, and is single-use")
        void refreshRotatesAndIsSingleUse() throws Exception {
            AuthTokens original = loginCompany();

            // A refresh with the original refresh token yields a brand-new pair.
            MvcResult rotatedResult = mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + original.refreshToken() + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("COMPANY"))
                    .andReturn();
            AuthTokens rotated = readTokens(rotatedResult);

            // New pair differs from the original tokens.
            org.assertj.core.api.Assertions.assertThat(rotated.accessToken())
                    .isNotEqualTo(original.accessToken());
            org.assertj.core.api.Assertions.assertThat(rotated.refreshToken())
                    .isNotEqualTo(original.refreshToken());

            // The rotated (new) access token is valid.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(rotated.accessToken()))
                            .header("User-Agent", UA))
                    .andExpect(status().isOk());

            // The old access token is dead after rotation.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(original.accessToken()))
                            .header("User-Agent", UA))
                    .andExpect(status().isUnauthorized());

            // The old refresh token is single-use: a replay is rejected.
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + original.refreshToken() + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @DisplayName("Refresh with a garbage/unknown token is rejected")
        void refreshWithUnknownTokenRejected() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"never-issued-token\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }
    }

    @Nested
    @DisplayName("Logout invalidation")
    class Logout {

        @Test
        @DisplayName("After logout neither the access nor refresh token works")
        void logoutRevokesAccessAndRefresh() throws Exception {
            AuthTokens tokens = loginCompany();

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                            .header("User-Agent", UA))
                    .andExpect(status().isNoContent());

            // The logged-out access token can no longer reach a protected endpoint.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                            .header("User-Agent", UA))
                    .andExpect(status().isUnauthorized());

            // The logged-out refresh token can no longer be used to rotate.
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
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
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                            .header("User-Agent", UA))
                    .andExpect(status().isOk());

            // The same token from another device is treated as theft and rejected.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                            .header("User-Agent", OTHER_UA))
                    .andExpect(status().isUnauthorized());

            // Theft tripwire nukes the whole session: the legit device is dead too.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                            .header("User-Agent", UA))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("A refresh attempted from a different device is rejected and revokes the session")
        void wrongFingerprintRefreshRejected() throws Exception {
            AuthTokens tokens = loginCompany();

            // Attacker replays the refresh token from another device.
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", OTHER_UA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                    .andExpect(status().isUnauthorized());

            // Whole session revoked: legit access token no longer works, and the
            // refresh token is dead even from the correct device.
            mockMvc.perform(get("/api/v1/companies/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                            .header("User-Agent", UA))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("User-Agent", UA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private AuthTokens loginCompany() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/company")
                        .header("User-Agent", UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNumber\":\"" + COMPANY_ORG + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return readTokens(result);
    }

    private AuthTokens readTokens(MvcResult result) throws java.io.IOException {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthTokens(
                body.get("accessToken").asText(),
                body.get("refreshToken").asText(),
                se.comerit.resurs.security.PrincipalRole.valueOf(body.get("role").asText()),
                body.get("name").asText());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
