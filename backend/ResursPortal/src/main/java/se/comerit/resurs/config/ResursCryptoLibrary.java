package se.comerit.resurs.config;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;

public interface ResursCryptoLibrary extends Library {

    int resurs_crypto_init(String keyFilePath);

    int resurs_encrypt_pii(String plaintext, Pointer nonce, long nonceLen,
            Pointer ciphertextOut, LongByReference ciphertextLen);

    int resurs_decrypt_pii(Pointer nonce, long nonceLen, Pointer ciphertext, long ciphertextLen,
            Pointer plaintextOut, LongByReference plaintextLen);

    int resurs_hmac_sha256(Pointer data, long dataLen, Pointer hmacOut, LongByReference hmacLen);

    void resurs_crypto_shutdown();
}
