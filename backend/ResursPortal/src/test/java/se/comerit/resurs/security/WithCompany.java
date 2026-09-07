package se.comerit.resurs.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithCompany.SecurityContextFactory.class)
public @interface WithCompany {

    long id() default 1L;

    String name() default "Malmö Fastigheter AB";

    String orgNumber() default "556000-1234";

    final class SecurityContextFactory extends AbstractUserPrincipalSecurityContextFactory<WithCompany> {
        @Override
        protected UserPrincipal principal(WithCompany withCompany) {
            return new CompanyPrincipal(withCompany.id(), withCompany.name(), withCompany.orgNumber());
        }
    }
}
