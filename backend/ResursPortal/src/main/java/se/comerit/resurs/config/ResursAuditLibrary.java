package se.comerit.resurs.config;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

public interface ResursAuditLibrary extends Library {

    /** SHA-256 digest length. */
    int RESURS_AUDIT_HASH_LEN = 32;
    /** Ed25519 signature length (fixed). */
    int RESURS_AUDIT_SIG_LEN = 64;
    /** Ed25519 public key length (fixed). */
    int RESURS_AUDIT_PUBKEY_LEN = 32;

    int RESURS_AUDIT_OK = 0;
    int RESURS_AUDIT_ERR_NOT_INIT = -1;
    int RESURS_AUDIT_ERR_INVALID_ARG = -2;
    int RESURS_AUDIT_ERR_KEY_IO = -3;
    int RESURS_AUDIT_ERR_BUFFER_SMALL = -4;
    int RESURS_AUDIT_ERR_INTERNAL = -5;

    /** Load the Ed25519 signing key (PEM) from {@code keyFilePath}. Call once at startup. */
    int resurs_audit_init(String keyFilePath);

    /**
     * Hash one audit entry chained to the previous entry's hash, and sign the hash.
     *
     * @param previousHash    {@code RESURS_AUDIT_HASH_LEN} bytes, or null for the first entry in a chain
     *                        (treated as 32 zero bytes)
     * @param entryJson       entry JSON
     * @param entryLength     length of {@code entryJson} in bytes (not the Java char count)
     * @param hashOut         out: {@code RESURS_AUDIT_HASH_LEN} bytes, written in full. On success
     *                        {@code hashOut = SHA-256(previousHash || entryJson)}
     * @param signatureOut    out: at least {@code RESURS_AUDIT_SIG_LEN} bytes
     * @param signatureLength in/out: caller sets the capacity of {@code signatureOut}; native code
     *                        sets it to the number of bytes written (always 64 for Ed25519)
     */
    int resurs_audit_chain_entry(
            Pointer previousHash, // 32 Bytes | null
            String entryJson, long entryLength,
            Pointer hashOut, Pointer signatureOut, LongByReference signatureLength);

    /**
     * Recompute every hash from the actual entry content and verify every signature.
     *
     * @param hashes            {@code entryCount * RESURS_AUDIT_HASH_LEN} bytes, in chain order
     * @param signatures        {@code entryCount} signatures, same order
     * @param signatureLens     {@code entryCount} {@code size_t} values, same order
     * @param entries           {@code entryCount} JSON blobs back-to-back
     * @param entryLens         {@code entryCount} {@code size_t} values, same order
     * @param entryCount        number of entries in the chain
     * @param entriesLen        total byte length of {@code entries}
     * @param signaturesLen     total byte length of {@code signatures}
     * @param publicKey         {@code RESURS_AUDIT_PUBKEY_LEN} bytes
     * @param firstInvalidIndex out: -1 if the whole chain verifies, otherwise the index of the
     *                        first entry failing either the hash or the signature check
     * @return {@code RESURS_AUDIT_OK} once verification ran to completion, regardless of the
     * result; error codes are reserved for operational failures (bad args, etc.)
     */
    int resurs_audit_verify_chain(
            Pointer hashes,
            Pointer signatures,
            LongByReference signatureLens,
            Pointer entries, LongByReference entryLens,
            long entryCount, long entriesLen, long signaturesLen,
            Pointer publicKey,
            IntByReference firstInvalidIndex);

    /**
     * Free the signing key (EVP_PKEY_free).
     * Safe to call multiple times and before init.
     */
    void resurs_audit_shutdown();
}
