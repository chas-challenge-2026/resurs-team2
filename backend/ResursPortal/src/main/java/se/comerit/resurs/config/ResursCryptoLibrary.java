package se.comerit.resurs.config;

import com.sun.jna.Library;
import com.sun.jna.ptr.LongByReference;

public interface ResursCryptoLibrary extends Library {

    int resurs_crypto_init(String keyFilePath);

    int resurs_encrypt_pii(String plaintext, byte[] nonce, long nonceLen,
            byte[] ciphertextOut, LongByReference ciphertextLen);

    int resurs_decrypt_pii(byte[] nonce, long nonceLen, byte[] ciphertext, long ciphertextLen,
            byte[] plaintextOut, LongByReference plaintextLen);

    int resurs_hmac_sha256(byte[] data, long dataLen, byte[] hmacOutm, LongByReference hmacLen);

    void resurs_crypto_shutdown();
}
