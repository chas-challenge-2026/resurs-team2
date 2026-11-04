package se.comerit.resurs.api.v1.service;

import java.util.HexFormat;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Signs audit log entries and verifies the chains they form, so that tampering
 * with
 * an entry's content, or with the order the entries were recorded in, can be
 * detected.
 */
public interface AuditSigningService {

    /** SHA-256 chain link length, in bytes. */
    int HASH_LEN = 32;

    /** Signature length, in bytes (Ed25519). */
    int SIGNATURE_LEN = 64;

    /**
     * Length of the public key {@link #verifyChain} verifies under, in bytes
     * (Ed25519).
     */
    int PUBLIC_KEY_LEN = 32;

    /**
     * Returned by {@link #verifyChain} when every entry of the chain checks out.
     */
    int VALID_CHAIN = -1;

    /**
     * One signed chain link: the raw chain hash and the signature over it.
     *
     * <p>
     * Both arrays are exactly {@link #HASH_LEN} and {@link #SIGNATURE_LEN} bytes.
     */
    record SignedEntry(@NonNull byte[] hash, @NonNull byte[] signature) {

        public SignedEntry {
            if (hash.length != HASH_LEN) {
                throw new IllegalArgumentException("hash must be " + HASH_LEN + " bytes, got " + hash.length);
            }
            if (signature.length != SIGNATURE_LEN) {
                throw new IllegalArgumentException("signature must be " + SIGNATURE_LEN + " bytes, got "
                        + signature.length);
            }
        }

        /**
         * Lowercase hex form of {@link #hash()}, i.e. the 64-character chain link
         * value.
         */
        @NonNull
        public String hashHex() {
            return HexFormat.of().formatHex(hash);
        }

        /** Lowercase hex form of {@link #signature()}. */
        @NonNull
        public String signatureHex() {
            return HexFormat.of().formatHex(signature);
        }
    }

    /**
     * Hashes one audit entry chained to the previous entry's hash and signs the
     * result.
     *
     * @param entryJson    the entry JSON, hashed as its UTF-8 encoding
     * @param previousHash the {@link #HASH_LEN} raw bytes returned by
     *                     {@link SignedEntry#hash()}
     *                     for the preceding entry, or {@code null} to start a new
     *                     chain
     * @return the chain link and its signature, as raw bytes
     * @throws IllegalArgumentException if {@code previousHash} is not a whole chain
     *                                  link
     */
    @NonNull
    SignedEntry signEntry(@NonNull String entryJson, @Nullable byte[] previousHash);

    /**
     * Recomputes every link of a chain from the entries' actual content and
     * verifies every
     * signature against {@code publicKey}.
     *
     * <p>
     * Passing the entries along with the links is what makes edited content
     * detectable:
     * a chain checked without them would only prove that the stored signatures
     * match the
     * stored hashes.
     *
     * @param hashes     the stored chain links, in chain order
     * @param signatures the stored signatures, in the same order
     * @param entries    the stored entry JSONs, in the same order
     * @param publicKey  the {@link #PUBLIC_KEY_LEN} raw bytes of the public key to
     *                   verify
     *                   under, i.e. the counterpart of the key the chain was signed
     *                   with
     * @return {@link #VALID_CHAIN} if the whole chain verifies, otherwise the
     *         zero-based
     *         index of the first entry that fails either its link or its signature
     *         check
     * @throws IllegalArgumentException if the lists do not describe one chain of
     *                                  the same
     *                                  length, or a value is not of the expected
     *                                  length
     */
    int verifyChain(@NonNull List<byte[]> hashes, @NonNull List<byte[]> signatures,
            @NonNull List<String> entries, @NonNull byte[] publicKey);

    /**
     * Argument check shared by the {@link #verifyChain} implementations, so that a
     * malformed chain is rejected identically whichever one is active. An empty
     * chain is
     * accepted; it has nothing to verify.
     *
     * @throws IllegalArgumentException if the lists do not describe one chain of
     *                                  the same
     *                                  length, or a value is not of the expected
     *                                  length
     */
    static void requireVerifiableChain(@NonNull List<byte[]> hashes, @NonNull List<byte[]> signatures,
            @NonNull List<String> entries, @NonNull byte[] publicKey) {
        int count = hashes.size();
        if (signatures.size() != count || entries.size() != count) {
            throw new IllegalArgumentException("hashes, signatures and entries must describe the same chain, got "
                    + count + "/" + signatures.size() + "/" + entries.size());
        }
        for (int i = 0; i < count; i++) {
            if (hashes.get(i).length != HASH_LEN) {
                throw new IllegalArgumentException("hashes[" + i + "] must be " + HASH_LEN + " raw bytes, got "
                        + hashes.get(i).length);
            }
            if (signatures.get(i).length != SIGNATURE_LEN) {
                throw new IllegalArgumentException("signatures[" + i + "] must be " + SIGNATURE_LEN + " raw bytes, got "
                        + signatures.get(i).length);
            }
        }
        if (publicKey.length != PUBLIC_KEY_LEN) {
            throw new IllegalArgumentException("publicKey must be " + PUBLIC_KEY_LEN + " raw bytes, got "
                    + publicKey.length);
        }
    }
}
