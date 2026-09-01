package se.comerit.resurs.crypto;

/**
 * Raised when PII encryption or decryption fails, including authentication
 * (GCM tag mismatch on decrypt) and native-module errors.
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
