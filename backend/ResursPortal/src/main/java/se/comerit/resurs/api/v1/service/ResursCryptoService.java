package se.comerit.resurs.api.v1.service;

/**
 * Service for encrypting/decrypting PII at rest and for deriving blind-index
 * values used for equality lookups without revealing the plaintext.
 *
 * <p>Each encrypted field is stored as a single blob returned by
 * {@link #encryptPii(String)} whose layout is {@code [nonce:NONCE_LEN][ciphertext]}
 * (for the test double the "ciphertext" section is the UTF-8 plaintext).
 * Lookup fields carry an additional {@link #blindIndex(String)} column whose
 * value is an HMAC over the canonicalized plaintext.
 */
public interface ResursCryptoService {

    int NONCE_LEN = 12;

    byte[] encryptPii(String plaintext);

    String decryptPii(byte[] blob);

    byte[] blindIndex(String value);

    byte[] generateNonce();
}