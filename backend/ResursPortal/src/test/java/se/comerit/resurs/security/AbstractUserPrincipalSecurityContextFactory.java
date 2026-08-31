package se.comerit.resurs.security;

import java.lang.annotation.Annotation;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * Builds a {@link SecurityContext} holding a {@link UserPrincipal} as the
 * authenticated principal, so controllers can rely on
 * {@code @AuthenticationPrincipal UserPrincipal} (e.g. the
 * {@code BackofficeController}'s {@code principal.asCaseWorker()}).
 */
abstract class AbstractUserPrincipalSecurityContextFactory<A extends Annotation>
        implements WithSecurityContextFactory<A> {

    protected abstract UserPrincipal principal(A annotation);

    @Override
    public SecurityContext createSecurityContext(A annotation) {
        UserPrincipal principal = principal(annotation);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}
