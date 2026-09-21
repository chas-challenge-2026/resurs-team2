package se.comerit.resurs.api.v1.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Encryption middleware for the document store.
 *
 * <p>Decorates any {@link FileStorageService} (local disk or S3), encrypting
 * file bytes at rest with the native AES-256-GCM crypto and decrypting on
 * read. The delegate only ever sees ciphertext, so the storage backend has no
 * knowledge of the plaintext content.</p>
 *
 * <p>The wrapper is swappable at the composition root ({@code FileStorageConfig}):
 * removing or disabling it (property {@code storage.encryption.enabled}) yields a
 * plain {@code FileStorageService} with no other code changes.</p>
 */
public class EncryptedFileStorageService implements FileStorageService {

    private final FileStorageService delegate;
    private final ResursCryptoService cryptoService;

    public EncryptedFileStorageService(FileStorageService delegate, ResursCryptoService cryptoService) {
        this.delegate = delegate;
        this.cryptoService = cryptoService;
    }

    @Override
    public String upload(UUID documentId, String originalFilename, InputStream inputStream, long size) throws IOException {
        byte[] plaintext = inputStream.readAllBytes();
        byte[] ciphertext = cryptoService.encryptRaw(plaintext);
        return delegate.upload(documentId, originalFilename,
                new ByteArrayInputStream(ciphertext), ciphertext.length);
    }

    @Override
    public Resource download(String storedFilename) throws IOException {
        byte[] ciphertext = delegate.download(storedFilename).getInputStream().readAllBytes();
        byte[] plaintext = cryptoService.decryptRaw(ciphertext);
        return new ByteArrayResource(plaintext);
    }

    @Override
    public void delete(String storedFilename) throws IOException {
        delegate.delete(storedFilename);
    }
}