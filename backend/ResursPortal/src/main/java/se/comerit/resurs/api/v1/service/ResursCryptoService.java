package se.comerit.resurs.api.v1.service;

public interface ResursCryptoService {

    byte[] encryptPii(String plaintext);

    String decryptPii(byte[] ciphertext, byte[] nonce);

    byte[] blindIndex(String value);

    byte[] generateNonce();
}
