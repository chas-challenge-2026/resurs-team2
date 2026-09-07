package se.comerit.resurs.api.v1.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class DummyCryptoService implements ResursCryptoService {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public byte[] encryptPii(String plaintext) {
        return plaintext.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String decryptPii(byte[] ciphertext, byte[] nonce) {
        return new String(ciphertext, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] blindIndex(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] generateNonce() {
        byte[] nonce = new byte[12];
        secureRandom.nextBytes(nonce);
        return nonce;
    }
}
