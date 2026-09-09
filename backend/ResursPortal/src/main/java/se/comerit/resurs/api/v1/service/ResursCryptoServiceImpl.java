package se.comerit.resurs.api.v1.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

import com.sun.jna.Memory;
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
        // NOTE: Allocation could possibly be removed if a non allocating size function
        // is used to determine the byte length of the text
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        int cipherLen = KEY_VERSION_LEN + plaintextBytes.length + TAG_LEN;
        int totalLen = NONCE_LEN + cipherLen;
        Memory buf = new Memory(totalLen);
        byte[] nonce = generateNonce();
        buf.write(0, nonce, 0, NONCE_LEN);

        LongByReference bufferLen = new LongByReference(cipherLen);
        int rc = library.resurs_encrypt_pii(plaintext, buf, NONCE_LEN,
                buf.share(NONCE_LEN), bufferLen);
        if (rc != 0) {
            throw new CryptoException(rc);
        }

        int written = (int) bufferLen.getValue();
        return buf.getByteArray(0, NONCE_LEN + written);
    }

    @Override
    public String decryptPii(byte[] blob) {
        if (blob.length < NONCE_LEN + MIN_CIPHERTEXT_LEN) {
            throw new CryptoException("Blob too short: " + blob.length);
        }

        Memory blobMem = new Memory(blob.length);
        blobMem.write(0, blob, 0, blob.length);

        int ciphertextLen = blob.length - NONCE_LEN;
        int plainLen = ciphertextLen - KEY_VERSION_LEN - TAG_LEN;
        LongByReference outLen = new LongByReference(plainLen);

        Memory plainBuf = new Memory(plainLen);

        int rc = library.resurs_decrypt_pii(blobMem, NONCE_LEN,
                blobMem.share(NONCE_LEN), ciphertextLen,
                plainBuf, outLen);
        if (rc != 0) {
            throw new CryptoException(rc);
        }

        int written = (int) outLen.getValue();
        byte[] plaintextBytes = plainBuf.getByteArray(0, written);
        return new String(plaintextBytes, 0, written, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] blindIndex(String value) {
        String canonical = canonicalize(value);
        byte[] data = canonical.getBytes(StandardCharsets.UTF_8);

        Memory dataMem = new Memory(data.length);
        dataMem.write(0, data, 0, data.length);

        Memory hmacBuf = new Memory(HMAC_LEN);
        LongByReference hmacLen = new LongByReference(HMAC_LEN);

        int rc = library.resurs_hmac_sha256(dataMem, data.length, hmacBuf, hmacLen);
        if (rc != 0) {
            throw new CryptoException(rc);
        }

        return hmacBuf.getByteArray(0, HMAC_LEN);
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
