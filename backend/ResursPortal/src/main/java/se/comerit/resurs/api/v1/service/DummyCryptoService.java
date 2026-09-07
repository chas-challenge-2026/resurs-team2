package se.comerit.resurs.api.v1.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import se.comerit.resurs.exception.CryptoException;

@Service
@Profile("test")
public class DummyCryptoService implements ResursCryptoService {

    @Override
    public byte[] encryptPii(String plaintext) {
        throw new CryptoException("Native crypto not initialised (test mode)");
    }

    @Override
    public String decryptPii(byte[] ciphertext, byte[] nonce) {
        throw new CryptoException("Native crypto not initialised (test mode)");
    }

    @Override
    public byte[] blindIndex(String value) {
        throw new CryptoException("Native crypto not initialised (test mode)");
    }

    @Override
    public byte[] generateNonce() {
        throw new CryptoException("Native crypto not initialised (test mode)");
    }
}
