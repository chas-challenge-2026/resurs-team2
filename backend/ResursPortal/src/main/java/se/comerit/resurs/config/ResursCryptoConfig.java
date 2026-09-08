package se.comerit.resurs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.sun.jna.Native;

import se.comerit.resurs.api.v1.service.DummyCryptoService;
import se.comerit.resurs.api.v1.service.ResursCryptoService;
import se.comerit.resurs.api.v1.service.ResursCryptoServiceImpl;
import se.comerit.resurs.exception.CryptoException;

@Configuration
@Profile("!test")
public class ResursCryptoConfig {

    private static final Logger log = LoggerFactory.getLogger(ResursCryptoConfig.class);

    @Value("${resurs.jna.key.path:}")
    private String keyPath;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Bean(destroyMethod = "resurs_crypto_shutdown")
    public ResursCryptoLibrary resursCryptoLibrary() {
        boolean productionDb = isProductionDatabase();
        ResursCryptoLibrary library;
        try {
            library = Native.load("resurs_crypto", ResursCryptoLibrary.class);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            if (productionDb) {
                throw new CryptoException(
                        "Native crypto library not available but a production database is configured. " +
                        "PII encryption is required. Build with 'make build-native' or set RESURS_CRYPTO_KEY_PATH.", e);
            }
            log.warn("PII-encryption is DISABLED: native crypto library 'resurs_crypto' was not found on the library path. " +
                    "Run 'make build-native' to build it.");
            return null;
        }

        if (!isKeyConfigured()) {
            if (productionDb) {
                throw new CryptoException(
                        "PII encryption key not configured (resurs.jna.key.path / RESURS_CRYPTO_KEY_PATH) " +
                        "but a production database is detected. Encryption is required for production use.");
            }
            log.warn("PII-encryption is DISABLED: no resurs.jna.key.path configured; native crypto loaded but not initialised");
            return null;
        }

        int rc = library.resurs_crypto_init(keyPath);
        if (rc != 0) {
            throw new CryptoException(rc);
        }
        log.info("Native crypto initialised, key={}", keyPath);
        return library;
    }

    @Bean
    public ResursCryptoService resursCryptoService(ObjectProvider<ResursCryptoLibrary> libraryProvider) {
        ResursCryptoLibrary library = libraryProvider.getIfAvailable();
        if (library == null) {
            if (isProductionDatabase()) {
                throw new CryptoException(
                        "Cannot start without PII encryption: production database detected but crypto library or key is unavailable.");
            }
            log.warn("Falling back to DummyCryptoService; PII encryption disabled");
            return new DummyCryptoService();
        }
        return new ResursCryptoServiceImpl(library);
    }

    private boolean isKeyConfigured() {
        return keyPath != null && !keyPath.isBlank();
    }

    private boolean isProductionDatabase() {
        return datasourceUrl != null && datasourceUrl.contains("postgresql");
    }
}