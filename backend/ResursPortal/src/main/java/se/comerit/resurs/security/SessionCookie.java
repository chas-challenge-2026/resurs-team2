package se.comerit.resurs.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Builds the two {@code httpOnly} session cookies that carry the access and
 * single-use refresh tokens between the browser and the API. Opaque to
 * JavaScript (HttpOnly + SameSite=Lax + Secure), so the frontend never reads
 * or presents token values. Both cookies share {@code Path=/} so the browser
 * sends the refresh cookie on every API request — that is what lets
 * {@link SessionTokenAuthenticationFilter} perform transparent server-side
 * rotation on any request, not just on the refresh endpoint.
 */
public final class SessionCookie {

    public static final String ACCESS = "resurs_access";
    public static final String REFRESH = "resurs_refresh";

    private static final String PATH = "/";
    private static final String SAME_SITE = "Lax";

    private SessionCookie() {
    }

    public static Cookie access(String token) {
        return tokenCookie(ACCESS, token);
    }

    public static Cookie refresh(String token) {
        return tokenCookie(REFRESH, token);
    }

    /** Clears the access cookie (matching name + path so the browser removes it). */
    public static Cookie clearAccess() {
        return clearedCookie(ACCESS);
    }

    /** Clears the refresh cookie (matching name + path so the browser removes it). */
    public static Cookie clearRefresh() {
        return clearedCookie(REFRESH);
    }

    public static String accessToken(HttpServletRequest request) {
        return value(request, ACCESS);
    }

    public static String refreshToken(HttpServletRequest request) {
        return value(request, REFRESH);
    }

    private static Cookie tokenCookie(String name, String token) {
        Cookie cookie = new Cookie(name, token);
        configure(cookie);
        return cookie;
    }

    private static Cookie clearedCookie(String name) {
        Cookie cookie = new Cookie(name, "");
        configure(cookie);
        cookie.setMaxAge(0);
        return cookie;
    }

    private static void configure(Cookie cookie) {
        cookie.setPath(PATH);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setAttribute("SameSite", SAME_SITE);
    }

    private static String value(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}