package se.comerit.resurs.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the JNA native-library search path as a JVM system property.
 *
 * <p>{@code jna.library.path} is a JVM system property read by JNA at the moment a
 * native library is resolved, not a Spring property. It therefore cannot be set
 * directly from {@code application.properties}. We expose it as the Spring property
 * {@code resurs.jna.library.path} (relative to the working dir) and copy it into the
 * system property here, before any {@code Native.load(...)} call.</p>
 */
@Configuration
public class JnaConfig {

    @Value("${resurs.jna.library.path:target/libs}")
    private String libraryPath;

    @PostConstruct
    void configureJnaLibraryPath() {
        if (System.getProperty("jna.library.path") == null) {
            System.setProperty("jna.library.path", libraryPath);
        }
    }
}
