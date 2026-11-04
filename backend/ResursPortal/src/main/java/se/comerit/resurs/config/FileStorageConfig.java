package se.comerit.resurs.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import se.comerit.resurs.api.v1.service.EncryptedFileStorageService;
import se.comerit.resurs.api.v1.service.FileStorageService;
import se.comerit.resurs.api.v1.service.LocalFileStorageService;
import se.comerit.resurs.api.v1.service.ResursCryptoService;
import se.comerit.resurs.api.v1.service.S3FileStorageService;

import java.net.URI;

/**
 * Composition root for the file storage pipeline.
 *
 * <p>Builds a single {@link FileStorageService} bean that DocumentService and
 * the controllers see. The pipeline is assembled here so each stage can be
 * swapped independently:</p>
 *
 * <ol>
 *   <li><b>Backend</b> – {@code storage.type=local|s3} picks the raw blob store
 *       (disk directory or an S3-compatible bucket).</li>
 *   <li><b>Encryption middleware</b> – {@code storage.encryption.enabled}
 *       (default {@code true}) wraps the backend in
 *       {@link EncryptedFileStorageService}, which encrypts file bytes at rest
 *       via the native AES-256-GCM module and decrypts on read. Set it to
 *       {@code false} (or delete the wrapper) to disable encryption without
 *       touching any other code.</li>
 * </ol>
 */
@Configuration
public class FileStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "s3")
    S3Client s3Client(@Value("${storage.s3.region:eu-north-1}") String region,
                      @Value("${storage.s3.endpoint:}") String endpoint) {
        var builder = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .region(Region.of(region));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    @Primary
    FileStorageService fileStorageService(
            ObjectProvider<LocalFileStorageService> localStorage,
            ObjectProvider<S3FileStorageService> s3Storage,
            ObjectProvider<ResursCryptoService> cryptoServiceProvider,
            @Value("${storage.encryption.enabled:true}") boolean encryptionEnabled) {

        FileStorageService raw = s3Storage.getIfAvailable();
        if (raw == null) {
            raw = localStorage.getIfAvailable();
        }
        if (raw == null) {
            throw new IllegalStateException(
                    "No file storage backend configured. Set storage.type=local or storage.type=s3.");
        }
        if (!encryptionEnabled) {
            return raw;
        }

        ResursCryptoService cryptoService = cryptoServiceProvider.getIfAvailable();
        if (cryptoService == null) {
            throw new IllegalStateException(
                    "storage.encryption.enabled=true but no ResursCryptoService bean is available.");
        }
        return new EncryptedFileStorageService(raw, cryptoService);
    }
}