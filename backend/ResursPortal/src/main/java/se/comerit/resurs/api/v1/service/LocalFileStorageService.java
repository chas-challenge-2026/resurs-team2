package se.comerit.resurs.api.v1.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Filesystem-backed {@link FileStorageService}. Active when
 * {@code storage.type=local} (the default). Files are stored under
 * {@code storage.path}, which in Docker is backed by the {@code storage_data}
 * named volume so documents survive container restarts.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    @Value("${storage.path:#{systemProperties['java.io.tmpdir'] + '/uploads'}}")
    private String storagePath;

    @PostConstruct
    public void init() throws IOException {
        Path path = Path.of(storagePath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    @Override
    public String upload(UUID documentId, String originalFilename, InputStream inputStream, long size) throws IOException {
        String storedFilename = documentId + ".pdf";
        Path targetPath = Path.of(storagePath, storedFilename);
        Files.copy(inputStream, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return storedFilename;
    }

    @Override
    public Resource download(String storedFilename) throws IOException {
        Path filePath = Path.of(storagePath, storedFilename);
        byte[] content = Files.readAllBytes(filePath);
        return new ByteArrayResource(content);
    }

    @Override
    public void delete(String storedFilename) throws IOException {
        Path filePath = Path.of(storagePath, storedFilename);
        Files.deleteIfExists(filePath);
    }
}

