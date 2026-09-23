package se.comerit.resurs.api.v1.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import se.comerit.resurs.security.AuthTokens;
import se.comerit.resurs.security.PrincipalRole;
import se.comerit.resurs.security.SessionCookie;
import se.comerit.resurs.security.SessionTokenStore;

/**
 * End-to-end verification of sliding session expiry through the real HTTP
 * stack (login → filter → {@link SessionTokenStore}):
 *
 * <ul>
 *   <li>interaction keeps sliding the session window forward,</li>
 *   <li>inactivity beyond the idle window invalidates the session,</li>
 *   <li>the absolute cap bounds even a continuously active session.</li>
 * </ul>
 *
 * A {@code @Primary} store with short idle/absolute windows is used so the
 * behaviour is observable in real time without slowing the build.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sliding;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM documents",
        "DELETE FROM applications",
        "DELETE FROM companies",
        "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (901, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Anders Karlsson')"
})
class SessionSlidingExpirationIntegrationTest {

    private static final String UA = "sliding-test-agent";
    private static final String COMPANY_ORG = "556000-1234";

    @TestConfiguration
    static class SlidingStoreConfig {
        @Bean
        @Primary
        SessionTokenStore shortLivedStore() {
            // idle 3s, absolute 8s — short enough to observe sliding and expiry
            // in real time, long enough (relative to the 2s request pacing) to
            // be deterministic.
            return new SessionTokenStore(true, 3000, 8000);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Interaction slides the window forward; inactivity beyond the idle window invalidates the session")
    void interactionSlidesWindowAndInactivityInvalidates() throws Exception {
        AuthTokens tokens = login();

        // t ~ 0s: authenticated; expiry sits ~3s out.
        assertOk(tokens);

        Thread.sleep(2000); // t ~ 2s — still inside the 3s idle window
        assertOk(tokens); // window slides forward (~5s)

        Thread.sleep(2000); // t ~ 4s — inside the slid window but PAST a FIXED 3s window
        assertOk(tokens); // proves sliding vs fixed expiry

        Thread.sleep(3500); // t ~ 7.5s — beyond the slid expiry (~7s)
        assertUnauthorized(tokens); // inactivity invalidated the session
    }

    @Test
    @DisplayName("The absolute cap terminates even a continuously active session")
    void absoluteCapBoundsContinuousActivity() throws Exception {
        AuthTokens tokens = login();

        // Four requests ~2s apart: all succeed because each interaction slides
        // the window — but the absolute 8s cap measured from login is what
        // finally terminates the session.
        assertOk(tokens); // t ~ 0s
        Thread.sleep(2000);
        assertOk(tokens); // t ~ 2s
        Thread.sleep(2000);
        assertOk(tokens); // t ~ 4s
        Thread.sleep(2000);
        assertOk(tokens); // t ~ 6s

        Thread.sleep(3000); // t ~ 9s — beyond the 8s absolute cap
        assertUnauthorized(tokens);
    }

    private AuthTokens login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/company")
                        .header("User-Agent", UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNumber\":\"" + COMPANY_ORG + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return new AuthTokens(
                cookieValue(result.getResponse(), SessionCookie.ACCESS),
                cookieValue(result.getResponse(), SessionCookie.REFRESH),
                PrincipalRole.COMPANY,
                "Malmö Fastigheter AB");
    }

    private static Cookie accessCookie(String token) {
        return new Cookie(SessionCookie.ACCESS, token);
    }

    private static String cookieValue(MockHttpServletResponse response, String name) {
        for (Cookie cookie : response.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        throw new AssertionError("Expected cookie '" + name + "' in the response");
    }

    private void assertOk(AuthTokens tokens) throws Exception {
        mockMvc.perform(get("/api/v1/companies/me")
                        .cookie(accessCookie(tokens.accessToken()))
                        .header("User-Agent", UA))
                .andExpect(status().isOk());
    }

    private void assertUnauthorized(AuthTokens tokens) throws Exception {
        mockMvc.perform(get("/api/v1/companies/me")
                        .cookie(accessCookie(tokens.accessToken()))
                        .header("User-Agent", UA))
                .andExpect(status().isUnauthorized());
    }
}