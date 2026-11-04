package se.comerit.resurs.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithCaseWorker.SecurityContextFactory.class)
public @interface WithCaseWorker {

    long id() default 1L;

    String name() default "Karin Handläggare";

    String email() default "karin@resurs.se";

    final class SecurityContextFactory extends AbstractUserPrincipalSecurityContextFactory<WithCaseWorker> {
        @Override
        protected UserPrincipal principal(WithCaseWorker withCaseWorker) {
            return new CaseWorkerPrincipal(withCaseWorker.id(), withCaseWorker.name(), withCaseWorker.email());
        }
    }
}
