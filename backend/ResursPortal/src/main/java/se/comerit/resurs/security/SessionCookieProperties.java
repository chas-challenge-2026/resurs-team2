package se.comerit.resurs.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable attributes for the session cookies built by {@link SessionCookie},
 * bound from {@code resurs.auth.cookie.*} in application.properties.
 *
 * <p>{@code HttpOnly} is always on and not configurable — tokens must stay
 * opaque to JavaScript. {@code Secure} defaults to {@code true} and should stay
 * on whenever the browser-facing origin is served over HTTPS (the platform's
 * edge terminates TLS). Keep it on for plain {@code http://localhost} too —
 * localhost is a secure context in Chromium and Firefox, so Secure cookies are
 * accepted there. Disable it only for plain-HTTP development against a
 * non-localhost host, where browsers silently drop Secure cookies and login
 * would otherwise appear broken.
 */
@ConfigurationProperties(prefix = "resurs.auth.cookie")
public record SessionCookieProperties(
        @DefaultValue("true") boolean secure,
        @DefaultValue("Lax") String sameSite) {
}