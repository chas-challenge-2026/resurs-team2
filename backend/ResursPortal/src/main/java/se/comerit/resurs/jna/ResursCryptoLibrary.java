package se.comerit.resurs.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.ptr.NativeLongByReference;

/**
 * JNA bridge to the native {@code libresurs_crypto} module.
 *
 * <p>Maps the C ABI declared in {@code native/crypto/resurs_crypto.h}. The
 * backing library is loaded from the platform's library path (see
 * {@code -Djna.library.path} / {@code /usr/local/lib}).</p>
 *
 * <p><strong>Thread safety:</strong> Spring services may call these functions
 * concurrently from multiple request threads. The native module keeps its keys
 * immutable after {@link #resurs_crypto_init(String)} and allocates per-call
 * contexts, making these calls safe to invoke from multiple threads.</p>
 *
 * <p>Output buffers follow the header's contract: the caller pre-sizes each
 * {@code _out} buffer to hold the maximum possible output and passes a
 * {@code size_t*} that on input carries the buffer capacity and on return is
 * overwritten with the actual number of bytes written.</p>
 */
public interface ResursCryptoLibrary extends Library {

    ResursCryptoLibrary INSTANCE = Native.load("resurs_crypto", ResursCryptoLibrary.class);

    /** AES-256 key length in bytes (header {@code RESURS_KEY_LEN}). */
    int RESURS_KEY_LEN = 32;
    /** GCM nonce length in bytes (header {@code RESURS_NONCE_LEN}). */
    int RESURS_NONCE_LEN = 12;
    /** GCM tag length in bytes (header {@code RESURS_TAG_LEN}). */
    int RESURS_TAG_LEN = 16;

    // Return codes from the header's enum.
    int RESURS_OK               = 0;
    int RESURS_ERR_NOT_INIT     = -1;
    int RESURS_ERR_AUTH         = -2;
    int RESURS_ERR_KEY_IO       = -3;
    int RESURS_ERR_BUFFER_SMALL = -4;
    int RESURS_ERR_INVALID_ARG  = -5;
    int RESURS_ERR_INTERNAL     = -6;

    /**
     * Loads the 32-byte key from {@code key_file_path}.
     * Call once at startup, before any encrypt/decrypt.
     *
     * @return {@link #RESURS_OK} or an error code ({@link #RESURS_ERR_KEY_IO},
     *         {@link #RESURS_ERR_INVALID_ARG}, {@link #RESURS_ERR_INTERNAL}).
     */
    int resurs_crypto_init(String key_file_path);

    /**
     * AES-256-GCM encrypt. The output of {@code ciphertextOut} is
     * {@code *ciphertext_len} bytes with the 16-byte GCM tag appended at the end.
     *
     * @param plaintext            UTF-8 plaintext to encrypt.
     * @param nonceOut             caller-allocated buffer of at least {@code RESURS_NONCE_LEN} bytes.
     * @param ciphertextOut        caller-allocated buffer large enough for ciphertext + tag.
     * @param ciphertextLen        in: capacity of {@code ciphertextOut};
     *                             out: actual bytes written (including the tag).
     * @return {@link #RESURS_OK} or an error code.
     */
    int resurs_encrypt_pii(String plaintext,
                           byte[] nonceOut,
                           byte[] ciphertextOut,
                           NativeLongByReference ciphertextLen);

    /**
     * AES-256-GCM decrypt.
     *
     * @param nonce            the nonce produced by {@link #resurs_encrypt_pii(String, byte[], byte[], NativeLongByReference)}.
     * @param ciphertext       ciphertext + 16-byte GCM tag, as returned by encrypt.
     * @param ciphertextLen    total bytes in {@code ciphertext} (including tag).
     * @param plaintextOut     caller-allocated buffer large enough for the plaintext.
     * @param plaintextLen     in: capacity of {@code plaintextOut};
     *                         out: actual plaintext bytes written.
     * @return {@link #RESURS_OK} or an error code ({@link #RESURS_ERR_AUTH} on tag mismatch).
     */
    int resurs_decrypt_pii(byte[] nonce,
                           byte[] ciphertext,
                           NativeLong ciphertextLen,
                           byte[] plaintextOut,
                           NativeLongByReference plaintextLen);

    /**
     * Releases native resources. Optional; call once at shutdown.
     */
    void resurs_crypto_shutdown();
}
