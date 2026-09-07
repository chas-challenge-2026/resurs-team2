package se.comerit.resurs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.sun.jna.Native;

import se.comerit.resurs.exception.CryptoException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
@Profile("!test")
public class ResursCryptoConfig {

    private static final Logger log = LoggerFactory.getLogger(ResursCryptoConfig.class);

    @Value("${resurs.jna.key.path:}")
    private String keyPath;

    private ResursCryptoLibrary library;

    @PostConstruct
    void init() {
        try {
            library = Native.load("resurs_crypto", ResursCryptoLibrary.class);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            if (isKeyConfigured()) {
                throw new CryptoException("Native crypto library not available: " + e.getMessage(), e);
            }
            log.warn("PII-encryption is DISABLED: native crypto library 'resurs_crypto' was not found on the library path. " +
                    "All encrypt/decrypt calls will throw CryptoException. Run 'make build-native' to build it.");
            return;
        }

        if (isKeyConfigured()) {
            int rc = library.resurs_crypto_init(keyPath);
            if (rc != 0) {
                throw new CryptoException(rc);
            }
            log.info("Native crypto initialised, key={}", keyPath);
        } else {
            log.warn("PII-encryption is DISABLED: no resurs.jna.key.path configured; native crypto loaded but not initialised");
        }
    }

    @PreDestroy
    void shutdown() {
        if (library != null) {
            library.resurs_crypto_shutdown();
        }
    }

    @Bean
    public ResursCryptoLibrary resursCryptoLibrary() {
        return library;
    }

    private boolean isKeyConfigured() {
        return keyPath != null && !keyPath.isBlank();
    }
}