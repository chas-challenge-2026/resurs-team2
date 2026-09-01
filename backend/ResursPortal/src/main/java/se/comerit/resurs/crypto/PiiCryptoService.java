package se.comerit.resurs.crypto;

/**
 * Domain-level abstraction for PII encryption. Implementations hide whether the
 * actual crypto happens in the native module (via JNA) or is a no-op test double.
 *
 * <p>The stored value is expected to carry the full output of the encrypt
 * operation (ciphertext plus its GCM tag, as produced by
 * {@link ResursCryptoLibrary#resurs_encrypt_pii(String, byte[], byte[], com.sun.jna.ptr.NativeLongByReference)})
 * alongside the nonce used for that operation.</p>
 */
public interface PiiCryptoService {

    /**
     * Returns {@link #encrypt(String)} output paired with its nonce, both as
     * opaque bytes suitable for persistence.
     */
    record EncryptedValue(byte[] nonce, byte[] ciphertext) {
        /**
         * {@code true} when this value is the no-op/dummy representation
         * (i.e. {@code ciphertext} is just the UTF-8 plaintext and no decryption
         * is needed). Identity comparison is used so a never-encrypted value can
         * never accidentally pass a plaintext check.
         */
        public boolean isPlaintext() {
            return this == PLAINTEXT_VALUE;
        }

        /** Sentinel returned by the dummy implementation. Never produced by real crypto. */
        public static final EncryptedValue PLAINTEXT_VALUE =
            new EncryptedValue(new byte[0], new byte[0]);
    }

    /**
     * Encrypts {@code plaintext} (UTF-8) at rest. Each call uses a fresh nonce
     * so equal inputs produce distinct ciphertext.
     *
     * @return the nonce + ciphertext to persist, or {@link EncryptedValue#PLAINTEXT_VALUE}
     *         from a no-op implementation.
     * @throws CryptoException    if encryption fails.
     * @throws NullPointerException if {@code plaintext} is {@code null}.
     */
    EncryptedValue encrypt(String plaintext);

    /**
     * Decrypts a value previously produced by {@link #encrypt(String)}.
     *
     * @return the original plaintext, or unconditional identity with the input
     *         when the value is the no-op sentinel.
     * @throws CryptoException    if authentication fails or decryption errors.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    String decrypt(EncryptedValue value);
}
