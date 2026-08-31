package se.comerit.resurs.security;

import java.io.IOException;
import java.util.List;

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
 * Fronts the R-protected {@code /api/**} chain: reads the {@code Bearer}
 * access token, validates it (token + fingerprint) via
 * {@link SessionTokenStore}, and — on success — populates the Spring Security
 * context with a {@code ROLE_}-prefixed authority derived from the resolved
 * principal. Invalid tokens are simply left unauthenticated so the chain's
 * authorization rules produce the correct 401/403.
 */
@Component
public class SessionTokenAuthenticationFilter extends OncePerRequestFilter {

    private final SessionTokenStore store;
    private final SessionFingerprint fingerprint;

    public SessionTokenAuthenticationFilter(SessionTokenStore store, SessionFingerprint fingerprint) {
        this.store = store;
        this.fingerprint = fingerprint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() > 7) {
            String token = header.substring(7);
            store.validateAccess(token, fingerprint.of(request)).ifPresent(principal -> {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        filterChain.doFilter(request, response);
    }

}
