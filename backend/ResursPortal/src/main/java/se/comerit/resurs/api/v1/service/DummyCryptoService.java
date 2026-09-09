package se.comerit.resurs.api.v1.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Test stand-in that mimics the layout of the real implementation without doing
 * any actual encryption: the "ciphertext" is the UTF-8 plaintext and the blind
 * index is the canonicalized value's SHA-256. The nonce prefix/length follows
 * {@link ResursCryptoService#NONCE_LEN} so callers can split the blob
 * identically regardless of which implementation is active.
 */
@Service
@Profile("test")
public class DummyCryptoService implements ResursCryptoService {

    private static final String SHA_256 = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public byte[] encryptPii(String plaintext) {
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] nonce = generateNonce();

        byte[] blob = new byte[nonce.length + plaintextBytes.length];
        System.arraycopy(nonce, 0, blob, 0, nonce.length);
        System.arraycopy(plaintextBytes, 0, blob, nonce.length, plaintextBytes.length);
        return blob;
    }

    @Override
    public String decryptPii(byte[] blob) {
        byte[] ciphertext = Arrays.copyOfRange(blob, NONCE_LEN, blob.length);
        return new String(ciphertext, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] blindIndex(String value) {
        try {
            return MessageDigest.getInstance(SHA_256)
                    .digest(canonicalize(value).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public byte[] generateNonce() {
        byte[] nonce = new byte[NONCE_LEN];
        secureRandom.nextBytes(nonce);
        return nonce;
    }

    private String canonicalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}