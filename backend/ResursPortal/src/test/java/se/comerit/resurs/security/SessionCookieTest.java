package se.comerit.resurs.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.Cookie;

/**
 * Unit tests for {@link SessionCookie}: the security attributes must always
 * include HttpOnly, while {@code Secure} and {@code SameSite} follow the
 * {@link SessionCookieProperties} configuration (the fix for the previously
 * hardcoded Secure flag).
 */
class SessionCookieTest {

    @Test
    void accessCookieCarriesDefaultSecurityAttributes() {
        Cookie cookie = session(true, "Lax").access("token");

        assertThat(cookie.getName()).isEqualTo(SessionCookie.ACCESS);
        assertThat(cookie.getValue()).isEqualTo("token");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void refreshCookieIsHttpOnlyAndSecure() {
        Cookie cookie = session(true, "Lax").refresh("token");

        assertThat(cookie.getName()).isEqualTo(SessionCookie.REFRESH);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void secureFlagFollowsConfiguration() {
        assertThat(session(false, "Lax").access("t").getSecure()).isFalse();
        assertThat(session(true, "Lax").access("t").getSecure()).isTrue();
    }

    @Test
    void httpOnlyIsNeverDisabledByConfiguration() {
        // Even with Secure off (plain-HTTP dev), HttpOnly must stay on.
        assertThat(session(false, "Lax").access("t").isHttpOnly()).isTrue();
    }

    @Test
    void sameSiteFollowsConfiguration() {
        assertThat(session(true, "Lax").access("t").getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(session(true, "Strict").access("t").getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(session(true, "None").access("t").getAttribute("SameSite")).isEqualTo("None");
    }

    @Test
    void clearedCookiesExpireImmediately() {
        Cookie access = session(true, "Lax").clearAccess();
        Cookie refresh = session(true, "Lax").clearRefresh();

        assertThat(access.getName()).isEqualTo(SessionCookie.ACCESS);
        assertThat(refresh.getName()).isEqualTo(SessionCookie.REFRESH);
        assertThat(access.getPath()).isEqualTo("/");
        assertThat(access.getMaxAge()).isZero();
        assertThat(refresh.getMaxAge()).isZero();
        assertThat(access.getValue()).isEmpty();
        assertThat(refresh.getValue()).isEmpty();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(refresh.isHttpOnly()).isTrue();
    }

    private static SessionCookie session(boolean secure, String sameSite) {
        return new SessionCookie(new SessionCookieProperties(secure, sameSite));
    }
}