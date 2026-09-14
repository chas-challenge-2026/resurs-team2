package se.comerit.resurs.config;

import java.util.Base64;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import se.comerit.resurs.api.v1.service.ResursCryptoService;

/**
 * Production codec: stores each PII field as the base64 of the single at-rest
 * blob {@code [nonce:NONCE_LEN][ciphertext]} in a character column.
 */
@Component
@Profile("!test")
public class EncryptedPiiCodec implements PiiCodec {

    private final ResursCryptoService crypto;

    public EncryptedPiiCodec(ResursCryptoService crypto) {
        this.crypto = crypto;
    }

    @Override
    public String encode(String plaintext) {
        return Base64.getEncoder().encodeToString(crypto.encryptPii(plaintext));
    }

    @Override
    public String decode(String stored) {
        return crypto.decryptPii(Base64.getDecoder().decode(stored));
    }
}