package se.comerit.resurs.crypto;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * No-op {@link PiiCryptoService} used when a JNA-loaded native module is not
 * available (the {@code test} and {@code local} H2 profiles). It stores
 * plaintext verbatim inside the {@link EncryptedValue} so entities and services
 * can be exercised end-to-end without real encryption.
 *
 * <p>This bean must never be active in a production-facing profile.</p>
 */
@Service
@Profile({"test", "local"})
public class DummyPiiCryptoService implements PiiCryptoService {

    @Override
    public EncryptedValue encrypt(String plaintext) {
        byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
        return new EncryptedValue(new byte[0], bytes);
    }

    @Override
    public String decrypt(EncryptedValue value) {
        return new String(value.ciphertext(), StandardCharsets.UTF_8);
    }
}
