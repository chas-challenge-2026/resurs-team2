package se.comerit.resurs.security;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * CSRF hardening layered on top of the {@code SameSite=Lax} session cookies.
 * SameSite=Lax stops cookies from being attached to cross-site requests, but a
 * {@code same-site} but {@code cross-origin} request (e.g. from another port or
 * a sibling subdomain) still carries them. For state-changing (non-safe) API
 * methods this filter rejects requests whose {@code Origin} host does not match
 * the request's own host, unless the origin is explicitly allow-listed.
 * <p>
 * An absent {@code Origin} header (curl and other non-browser clients) is
 * accepted: browsers always send {@code Origin} on cross-origin POSTs, so the
 * check only needs to reject the ones that identify themselves as foreign.
 * The literal origin {@code null} (sandboxed/file origins) is rejected.
 * <p>
 * The comparison deliberately ignores the port so setups where the reverse
 * proxy rewrites the {@code Host} header (or omits the port) keep working;
 * cross-host/subdomain attacks are still blocked. Use
 * {@code resurs.auth.allowed-origins} for legitimate browsers whose origin
 * host differs from the backend's {@code Host} (e.g. the Vite dev proxy on
 * :5173 with {@code changeOrigin}).
 */
@Component
public class CsrfOriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final URI PROBLEM_TYPE_DEFAULT = URI.create("about:blank");

    private final boolean enabled;
    private final Set<String> allowedOrigins;
    private final ObjectMapper objectMapper;

    public CsrfOriginCheckFilter(
            @Value("${resurs.auth.origin-check.enabled:true}") boolean enabled,
            @Value("${resurs.auth.allowed-origins:}") String allowedOrigins,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.allowedOrigins = parseAllowedOrigins(allowedOrigins);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank() || isAllowed(origin, request)) {
            filterChain.doFilter(request, response);
            return;
        }
        writeRejected(response);
    }

    private boolean isAllowed(String origin, HttpServletRequest request) {
        if ("null".equalsIgnoreCase(origin)) {
            // Browsers send the literal string "null" for sandboxed/file:// origins.
            return false;
        }
        URI uri = originUri(origin);
        if (uri == null || uri.getScheme() == null || uri.getHost() == null
                || !ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (uri.getHost().equalsIgnoreCase(request.getServerName())) {
            return true;
        }
        return allowedOrigins.contains(origin.toLowerCase(Locale.ROOT));
    }

    private static URI originUri(String origin) {
        try {
            return new URI(origin);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static Set<String> parseAllowedOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private void writeRejected(HttpServletResponse response) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Cross-origin request rejected");
        problemDetail.setTitle("Access Denied");
        problemDetail.setType(PROBLEM_TYPE_DEFAULT);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}