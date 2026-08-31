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
        filter = new SessionTokenAuthenticationFilter(store, fingerprint);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthenticationForValidBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");
        request.addHeader("Authorization", "Bearer some-token");
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
        request.addHeader("Authorization", "Bearer some-token");
        when(fingerprint.of(any())).thenReturn("UA|ip");
        when(store.validateAccess(eq("some-token"), any())).thenReturn(Optional.of(worker));

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CASE_WORKER");
    }

    @Test
    void skipsWhenNoAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(store, never()).validateAccess(any(), any());
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void skipsWhenHeaderNotBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(store, never()).validateAccess(any(), any());
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void passesThroughOnInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");
        request.addHeader("Authorization", "Bearer bogus");
        when(fingerprint.of(any())).thenReturn("UA|ip");
        when(store.validateAccess(eq("bogus"), any())).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doesNotCreateServerSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/ping");
        request.addHeader("Authorization", "Bearer some-token");
        when(fingerprint.of(any())).thenReturn("UA|ip");
        when(store.validateAccess(eq("some-token"), any())).thenReturn(Optional.of(principal));

        filter.doFilter(request, response, chain);

        // STATELESS API contract: nothing must force an HttpSession.
        assertThat(request.getSession(false)).isNull();
    }
}
