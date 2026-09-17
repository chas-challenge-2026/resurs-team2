package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * Verifies the encryption middleware wraps a raw storage backend so that only
 * ciphertext reaches the delegate, and plaintext round-trips on download.
 */
class EncryptedFileStorageServiceTest {

    /** Records everything written to it; never sees plaintext when wrapped. */
    private static final class InMemoryStorage implements FileStorageService {
        final Map<String, byte[]> files = new HashMap<>();
        final Map<String, String> uploadedOriginalNames = new HashMap<>();

        @Override
        public String upload(UUID documentId, String originalFilename, java.io.InputStream inputStream, long size) throws IOException {
            String key = documentId + ".pdf";
            files.put(key, inputStream.readAllBytes());
            uploadedOriginalNames.put(key, originalFilename);
            return key;
        }

        @Override
        public Resource download(String storedFilename) throws IOException {
            byte[] content = files.get(storedFilename);
            if (content == null) throw new java.io.FileNotFoundException(storedFilename);
            return new ByteArrayResource(content);
        }

        @Override
        public void delete(String storedFilename) {
            files.remove(storedFilename);
        }
    }

    private final InMemoryStorage raw = new InMemoryStorage();
    private final EncryptedFileStorageService encrypted =
            new EncryptedFileStorageService(raw, new DummyCryptoService());

    @Test
    void uploadStoresCiphertextNotPlaintext() throws Exception {
        byte[] plaintext = "%-PDF sensitive content".getBytes(StandardCharsets.UTF_8);
        UUID documentId = UUID.randomUUID();

        String key = encrypted.upload(documentId, "annual-review.pdf",
                new ByteArrayInputStream(plaintext), plaintext.length);

        assertThat(key).isEqualTo(documentId + ".pdf");
        byte[] stored = raw.files.get(key);
        assertThat(stored).isNotNull();
        assertThat(stored).isNotEqualTo(plaintext);
        assertThat(stored.length).isGreaterThan(plaintext.length); // nonce prefix present
    }

    @Test
    void downloadReturnsOriginalPlaintext() throws Exception {
        byte[] plaintext = "%PDF-1.4\nsome annual review".getBytes(StandardCharsets.UTF_8);
        UUID documentId = UUID.randomUUID();

        String key = encrypted.upload(documentId, "annual-review.pdf",
                new ByteArrayInputStream(plaintext), plaintext.length);

        byte[] downloaded = encrypted.download(key).getInputStream().readAllBytes();
        assertThat(downloaded).isEqualTo(plaintext);
    }

    @Test
    void uploadAndDownloadRoundTripPreservesBinaryContent() throws Exception {
        byte[] binary = new byte[256];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) i; // includes NULs and all byte values
        }
        UUID documentId = UUID.randomUUID();

        String key = encrypted.upload(documentId, "binary.pdf",
                new ByteArrayInputStream(binary), binary.length);

        byte[] downloaded = encrypted.download(key).getInputStream().readAllBytes();
        assertThat(downloaded).isEqualTo(binary);
    }

    @Test
    void deleteDelegatesToRawStorage() throws Exception {
        UUID documentId = UUID.randomUUID();
        String key = encrypted.upload(documentId, "x.pdf",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), 5);

        assertThat(raw.files).containsKey(key);
        encrypted.delete(key);
        assertThat(raw.files).doesNotContainKey(key);
    }
}