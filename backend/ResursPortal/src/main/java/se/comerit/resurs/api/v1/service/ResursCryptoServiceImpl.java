package se.comerit.resurs.api.v1.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

import com.sun.jna.ptr.LongByReference;

import se.comerit.resurs.config.ResursCryptoLibrary;
import se.comerit.resurs.exception.CryptoException;

public class ResursCryptoServiceImpl implements ResursCryptoService {

    private static final int NONCE_LEN = 12;
    private static final int TAG_LEN = 16;
    private static final int KEY_VERSION_LEN = 1;
    private static final int HMAC_LEN = 32;
    private static final int MIN_CIPHERTEXT_LEN = KEY_VERSION_LEN + TAG_LEN + 1;

    private final ResursCryptoLibrary library;
    private final SecureRandom secureRandom = new SecureRandom();

    public ResursCryptoServiceImpl(ResursCryptoLibrary library) {
        this.library = library;
    }

    @Override
    public byte[] encryptPii(String plaintext) {
        // Note: Could be optimized by having the native module write into an offset result struct directly.
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        int cipherLen = KEY_VERSION_LEN + plaintextBytes.length + TAG_LEN;

        byte[] ciphertext = new byte[cipherLen];

        byte[] nonce = generateNonce();
        long nonceLen = nonce.length;
        LongByReference bufferLen = new LongByReference(ciphertext.length);
        int rc = library.resurs_encrypt_pii(plaintext, nonce, nonceLen, ciphertext, bufferLen);
        if (rc != 0) {
            throw new CryptoException(rc);
        }

        int written = (int)bufferLen.getValue();
        byte[] result = new byte[nonce.length + written];
        System.arraycopy(nonce, 0, result, 0, nonce.length);
        System.arraycopy(ciphertext, 0, result, nonce.length, written);
        return result;
    }

    @Override
    public String decryptPii(byte[] blob) {
        byte[] nonce = Arrays.copyOfRange(blob, 0, NONCE_LEN);
        long nonceLen = NONCE_LEN;
        byte[] ciphertext = Arrays.copyOfRange(blob, NONCE_LEN, blob.length);
        if (ciphertext.length < MIN_CIPHERTEXT_LEN) {
            throw new CryptoException("Ciphertext too short: " + ciphertext.length);
        }

        int plainLen = ciphertext.length - KEY_VERSION_LEN - TAG_LEN;
        byte[] plaintextBuf = new byte[plainLen];
        LongByReference outLen = new LongByReference(plainLen);

        int rc = library.resurs_decrypt_pii(nonce, nonceLen, ciphertext, ciphertext.length,
                plaintextBuf, outLen);
        if (rc != 0) {
            throw new CryptoException(rc);
        }

        int written = (int) outLen.getValue();
        return new String(plaintextBuf, 0, written, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] blindIndex(String value) {
        String canonical = canonicalize(value);
        byte[] data = canonical.getBytes(StandardCharsets.UTF_8);
        byte[] hmac = new byte[HMAC_LEN];
        LongByReference hmacLen = new LongByReference(HMAC_LEN);

        int rc = library.resurs_hmac_sha256(data, data.length, hmac, hmacLen);
        if (rc != 0) {
            throw new CryptoException(rc);
        }

        return hmac;
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