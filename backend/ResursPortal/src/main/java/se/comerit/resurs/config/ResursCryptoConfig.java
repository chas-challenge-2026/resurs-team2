package se.comerit.resurs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sun.jna.Native;

import se.comerit.resurs.exception.CryptoException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
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
            log.warn("Native crypto library not available: {}", e.getMessage());
            return;
        }

        if (keyPath != null && !keyPath.isBlank()) {
            int rc = library.resurs_crypto_init(keyPath);
            if (rc != 0) {
                throw new CryptoException(rc);
            }
            log.info("Native crypto initialised, key={}", keyPath);
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
}
