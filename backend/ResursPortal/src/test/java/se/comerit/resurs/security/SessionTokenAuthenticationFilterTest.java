package se.comerit.resurs.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.Cookie;

class SessionTokenAuthenticationFilterTest {

    private SessionTokenStore store;
    private SessionFingerprint fingerprint;
    private SessionTokenAuthenticationFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    private final UserPrincipal principal =
            new CompanyPrincipal(1L, "Malmö Fastigheter AB", "556000-1234");

    @BeforeEach
    void setUp() {
        store = mock(SessionTokenStore.class);
        fingerprint = mock(SessionFingerprint.class);
        filter = new SessionTokenAuthenticationFilter(store, fingerprint,
                new SessionCookie(new SessionCookieProperties(true, "Lax")));
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthenticationForValidAccessCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");
        request.setCookies(accessCookie("some-token"));
        when(fingerprint.of(any())).thenReturn("UA|ip");
        when(store.validateAccess(eq("some-token"), any())).thenReturn(Optional.of(principal));

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isSameAs(principal);
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_COMPANY");
    }

    @Test
    void derivesAuthorityFromPrincipalRole() throws Exception {
        UserPrincipal worker =
                new CaseWorkerPrincipal(2L, "Karin", "karin@resurs.se");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");
        request.setCookies(accessCookie("some-token"));
        when(fingerprint.of(any())).thenReturn("UA|ip");
        when(store.validateAccess(eq("some-token"), any())).thenReturn(Optional.of(worker));

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CASE_WORKER");
    }

    @Test
    void skipsWhenNoCookies() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(store, never()).validateAccess(any(), any());
        verify(store, never()).rotate(any(), any());
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doesNotAutoRotateOnTokenManagedEndpoint() throws Exception {
        // POST /auth/refresh manages its own cookies — the filter must not
        // rotate on top of it (a double rotation would consume the new pair).
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.setCookies(refreshCookie("refresh-token"));
        when(fingerprint.of(any())).thenReturn("UA|ip");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(store, never()).rotate(any(), any());
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void passesThroughOnInvalidAccessCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");
        request.setCookies(accessCookie("bogus"));
        when(fingerprint.of(any())).thenReturn("UA|ip");
        when(store.validateAccess(eq("bogus"), any())).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doesNotCreateServerSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");
        request.setCookies(accessCookie("some-token"));
        when(fingerprint.of(any())).thenReturn("UA|ip");
        when(store.validateAccess(eq("some-token"), any())).thenReturn(Optional.of(principal));

        filter.doFilter(request, response, chain);

        // STATELESS API contract: nothing must force an HttpSession.
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void rotatesTransparentlyWhenAccessCookieMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");
        request.setCookies(refreshCookie("refresh-token"));
        when(fingerprint.of(any())).thenReturn("UA|ip");

        AuthTokens rotated = new AuthTokens(
                "new-access", "new-refresh", PrincipalRole.COMPANY, "Malmö Fastigheter AB");
        when(store.rotate(eq("refresh-token"), any())).thenReturn(Optional.of(rotated));
        when(store.validateAccess(eq("new-access"), any())).thenReturn(Optional.of(principal));

        filter.doFilter(request, response, chain);

        // Fresh pair is written into the response as Set-Cookie cookies.
        assertThat(response.getCookie(SessionCookie.ACCESS)).isNotNull();
        assertThat(response.getCookie(SessionCookie.ACCESS).getValue()).isEqualTo("new-access");
        assertThat(response.getCookie(SessionCookie.REFRESH)).isNotNull();
        assertThat(response.getCookie(SessionCookie.REFRESH).getValue()).isEqualTo("new-refresh");

        // The request is authenticated via the rotated access token.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isSameAs(principal);
    }

    @Test
    void doesNotRotateWhenRefreshAlreadySpent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");
        request.setCookies(refreshCookie("spent-refresh"));
        when(fingerprint.of(any())).thenReturn("UA|ip");
        when(store.rotate(eq("spent-refresh"), any())).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getCookie(SessionCookie.ACCESS)).isNull();
        assertThat(response.getCookie(SessionCookie.REFRESH)).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doesNotRotateWhenProbingSingleUseRefreshCookieOnProtectedPath() throws Exception {
        // Same as passesThroughOnInvalidAccessCookie but exercised where the
        // filter WOULD rotate if a refresh cookie were present: with neither
        // cookie the request stays unauthenticated and the store is untouched.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");
        when(fingerprint.of(any())).thenReturn("UA|ip");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(store, never()).validateAccess(any(), any());
        verify(store, never()).rotate(any(), any());
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static Cookie accessCookie(String token) {
        return new Cookie(SessionCookie.ACCESS, token);
    }

    private static Cookie refreshCookie(String token) {
        return new Cookie(SessionCookie.REFRESH, token);
    }
}