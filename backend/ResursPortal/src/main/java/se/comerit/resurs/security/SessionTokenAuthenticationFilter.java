package se.comerit.resurs.security;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fronts the API-protected {@code /api/**} chain: reads the {@code resurs_access}
 * session cookie, validates it (token + fingerprint) via {@link SessionTokenStore},
 * and — on success — populates the Spring Security context with a {@code ROLE_}-
 * prefixed authority derived from the resolved principal.
 * <p>
 * When the request is not authenticated but carries a valid {@code resurs_refresh}
 * cookie, the pair is rotated transparently: fresh cookies are {@code Set-Cookie}d
 * on the response and the new access token authenticates the request. This is what
 * lets the frontend restore a session on reload with a single {@code GET /me}
 * (or any API call) — no client-side refresh dance. Rotation is skipped on the
 * auth endpoints that manage their own cookies (login/refresh/logout).
 * <p>
 * Invalid tokens are simply left unauthenticated so the chain's authorization
 * rules produce the correct 401/403.
 */
@Component
public class SessionTokenAuthenticationFilter extends OncePerRequestFilter {

    /** Endpoints that read/manage the cookies themselves must not auto-rotate. */
    private static final Set<String> TOKEN_MANAGED_PATHS = Set.of(
            "/api/v1/auth/login/company",
            "/api/v1/auth/login/caseWorker",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/logout/all");

    private final SessionTokenStore store;
    private final SessionFingerprint fingerprint;
    private final SessionCookie sessionCookie;

    public SessionTokenAuthenticationFilter(SessionTokenStore store, SessionFingerprint fingerprint,
            SessionCookie sessionCookie) {
        this.store = store;
        this.fingerprint = fingerprint;
        this.sessionCookie = sessionCookie;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String fp = fingerprint.of(request);

        String accessToken = SessionCookie.accessToken(request);
        if (accessToken != null) {
            store.validateAccess(accessToken, fp).ifPresent(principal -> authenticate(principal, request));
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null && !tokenManaged(request)) {
            String refreshToken = SessionCookie.refreshToken(request);
            if (refreshToken != null) {
                store.rotate(refreshToken, fp).ifPresent(tokens -> {
                    response.addCookie(sessionCookie.access(tokens.accessToken()));
                    response.addCookie(sessionCookie.refresh(tokens.refreshToken()));
                    // The rotated (brand-new) pair is valid by construction, but
                    // validateAccess also resolves the UserPrincipal to authenticate with.
                    store.validateAccess(tokens.accessToken(), fp)
                            .ifPresent(principal -> authenticate(principal, request));
                });
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(UserPrincipal principal, HttpServletRequest request) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static boolean tokenManaged(HttpServletRequest request) {
        return TOKEN_MANAGED_PATHS.contains(request.getRequestURI());
    }

}