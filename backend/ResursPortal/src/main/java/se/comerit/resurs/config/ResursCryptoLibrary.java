package se.comerit.resurs.config;

import com.sun.jna.Library;

public interface ResursCryptoLibrary extends Library {

    int resurs_crypto_init(String keyFilePath);

    int resurs_encrypt_pii(String plaintext, byte[] nonce,
                           byte[] ciphertextOut, long[] ciphertextLen);

    int resurs_decrypt_pii(byte[] nonce, byte[] ciphertext, long ciphertextLen,
                           byte[] plaintextOut, long[] plaintextLen);

    int resurs_hmac_sha256(byte[] data, long dataLen, byte[] hmacOut);

    void resurs_crypto_shutdown();
}
