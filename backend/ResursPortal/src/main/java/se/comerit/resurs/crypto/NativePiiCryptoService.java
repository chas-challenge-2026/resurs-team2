package se.comerit.resurs.crypto;

import java.nio.charset.StandardCharsets;

import com.sun.jna.NativeLong;
import com.sun.jna.ptr.NativeLongByReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import se.comerit.resurs.jna.ResursCryptoLibrary;

/**
 * Production {@link PiiCryptoService} that delegates to the native
 * {@code libresurs_crypto} module via JNA ({@link ResursCryptoLibrary}).
 *
 * <p>Owns the buffer sizing and in/out length bookkeeping required by the C
 * ABI. The output buffer layout returned by the native encrypt is
 * {@code [ciphertext][16-byte GCM tag]} and the full buffer is what gets
 * persisted in {@link EncryptedValue#ciphertext()}.</p>
 *
 * <p>Active on the default (production) profile only. The {@code test} and
 * {@code local} H2 profiles use {@link DummyPiiCryptoService} instead, since the
 * native module is not loaded there.</p>
 */
@Service
@Profile("!test & !local")
public class NativePiiCryptoService implements PiiCryptoService {

    private final ResursCryptoLibrary library;

    public NativePiiCryptoService(ResursCryptoLibrary library) {
        this.library = library;
    }

    @Override
    public EncryptedValue encrypt(String plaintext) {
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        if (plaintextBytes.length > Integer.MAX_VALUE - ResursCryptoLibrary.RESURS_TAG_LEN) {
            throw new CryptoException("plaintext too large to encrypt");
        }
        byte[] nonce = new byte[ResursCryptoLibrary.RESURS_NONCE_LEN];
        int capacity = plaintextBytes.length + ResursCryptoLibrary.RESURS_TAG_LEN;
        byte[] ciphertext = new byte[capacity];
        NativeLongByReference ciphertextLen = new NativeLongByReference(new NativeLong(capacity));

        int rc = library.resurs_encrypt_pii(plaintext, nonce, ciphertext, ciphertextLen);
        if (rc != ResursCryptoLibrary.RESURS_OK) {
            throw new CryptoException("native encrypt failed, rc=" + rc);
        }
        int written = (int) ciphertextLen.getValue().longValue();
        byte[] out = new byte[written];
        System.arraycopy(ciphertext, 0, out, 0, written);
        return new EncryptedValue(nonce, out);
    }

    @Override
    public String decrypt(EncryptedValue value) {
        if (value.isPlaintext()) {
            // No-op sentinel from the dummy implementation.
            return new String(value.ciphertext(), StandardCharsets.UTF_8);
        }
        byte[] ciphertext = value.ciphertext();
        NativeLongByReference plaintextLen =
            new NativeLongByReference(new NativeLong(ciphertext.length));
        byte[] plaintext = new byte[ciphertext.length];

        int rc = library.resurs_decrypt_pii(
            value.nonce(),
            ciphertext,
            new NativeLong(ciphertext.length),
            plaintext,
            plaintextLen);
        if (rc != ResursCryptoLibrary.RESURS_OK) {
            throw new CryptoException("native decrypt failed, rc=" + rc);
        }
        int written = (int) plaintextLen.getValue().longValue();
        return new String(plaintext, 0, written, StandardCharsets.UTF_8);
    }
}
