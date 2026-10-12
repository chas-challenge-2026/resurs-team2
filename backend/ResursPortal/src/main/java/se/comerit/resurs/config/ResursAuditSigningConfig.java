package se.comerit.resurs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

import com.sun.jna.Native;

import se.comerit.resurs.api.v1.service.AuditSigningService;
import se.comerit.resurs.api.v1.service.DummyAuditSigningService;
import se.comerit.resurs.api.v1.service.NativeAuditSigningService;
import se.comerit.resurs.api.v1.service.ResursAuditServiceImpl;
import se.comerit.resurs.exception.CryptoException;

@Configuration
@Profile("!test")
public class ResursAuditSigningConfig {

    private static final Logger log = LoggerFactory.getLogger(ResursAuditSigningConfig.class);

    @Value("${resurs.audit.key.path:}")
    private String auditKeyPath;

    @Bean(destroyMethod = "resurs_audit_shutdown")
    @DependsOn("jnaConfig")
    public ResursAuditLibrary resursAuditLibrary() {
        ResursAuditLibrary library;
        try {
            library = Native.load("resurs_audit", ResursAuditLibrary.class);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("Audit signing DISABLED: native library 'resurs_audit' was not found on the library path. "
                    + "Run 'make build-native' to build it.");
            return null;
        }

        if (auditKeyPath == null || auditKeyPath.isBlank()) {
            log.warn("Audit signing DISABLED: no resurs.audit.key.path configured; native audit library loaded "
                    + "but not initialised");
            return null;
        }

        // A configured but unusable key is an operator error, not a reason to fall back
        // to
        // a signer that proves nothing: fail loudly rather than quietly weakening the
        // chain.
        int rc = library.resurs_audit_init(auditKeyPath);
        if (rc != ResursAuditLibrary.RESURS_AUDIT_OK) {
            throw new CryptoException(rc);
        }
        log.info("Native audit signing initialised, key={}", auditKeyPath);
        return library;
    }

    @Bean
    public AuditSigningService auditSigningService(ObjectProvider<ResursAuditLibrary> libraryProvider) {
        ResursAuditLibrary library = libraryProvider.getIfAvailable();
        if (library == null) {
            log.warn("Falling back to DummyAuditSigningService; audit chains are signed with a publicly "
                    + "known test key and prove nothing");
            return new DummyAuditSigningService();
        }
        // ResursAuditServiceImpl is deliberately not exposed as a bean: it is the JNA
        // layer
        // behind this service, not an entry point of its own.
        return new NativeAuditSigningService(new ResursAuditServiceImpl(library));
    }
}
