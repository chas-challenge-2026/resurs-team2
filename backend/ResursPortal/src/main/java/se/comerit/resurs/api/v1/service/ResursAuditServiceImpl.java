package se.comerit.resurs.api.v1.service;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

import se.comerit.resurs.config.ResursAuditLibrary;
import se.comerit.resurs.exception.CryptoException;

/**
 * Signs audit entries and verifies their chain.
 *
 * <p>Hashes and signatures are returned as raw bytes. Use {@link SignedEntry#hashHex()} and
 * {@link SignedEntry#signatureHex()} when a value has to be rendered or transported as text.
 */
public class ResursAuditServiceImpl {

    private static final int HASH_LEN = ResursAuditLibrary.RESURS_AUDIT_HASH_LEN;
    private static final int SIG_LEN = ResursAuditLibrary.RESURS_AUDIT_SIG_LEN;
    private static final int PUBKEY_LEN = ResursAuditLibrary.RESURS_AUDIT_PUBKEY_LEN;

    /** {@code size_t} on every platform this module is built for; also how JNA maps the length arrays. */
    private static final int SIZE_T_LEN = Long.BYTES;

    /** Returned by the native side when the whole chain verifies. */
    private static final int VALID_CHAIN = -1;

    private final ResursAuditLibrary library;

    public ResursAuditServiceImpl(ResursAuditLibrary library) {
        this.library = library;
    }

    /**
     * One signed chain link: the raw chain hash and the Ed25519 signature over it.
     *
     * <p>Both arrays are exactly {@link ResursAuditLibrary#RESURS_AUDIT_HASH_LEN} and
     * {@link ResursAuditLibrary#RESURS_AUDIT_SIG_LEN} bytes.
     */
    public record SignedEntry(@NonNull byte[] hash, @NonNull byte[] signature) {

        public SignedEntry {
            if (hash.length != HASH_LEN) {
                throw new IllegalArgumentException("hash must be " + HASH_LEN + " bytes, got " + hash.length);
            }
            if (signature.length != SIG_LEN) {
                throw new IllegalArgumentException("signature must be " + SIG_LEN + " bytes, got " + signature.length);
            }
        }

        /** Lowercase hex form of {@link #hash()}, i.e. the 64-character chain link value. */
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
     * Hashes one audit entry chained to the previous entry's hash and signs the result.
     *
     * @param entryJson    the entry JSON, hashed as its UTF-8 encoding
     * @param previousHash the 32 raw bytes returned by {@link SignedEntry#hash()} for the
     *                     preceding entry, or {@code null} to start a new chain
     * @return the chain hash and its signature, as raw bytes
     * @throws CryptoException          if the native library reports a failure
     * @throws IllegalArgumentException if {@code previousHash} is not exactly 32 bytes
     */
    @NonNull
    public SignedEntry signEntry(@NonNull String entryJson, @Nullable byte[] previousHash) {
        if (previousHash != null && previousHash.length != HASH_LEN) {
            // Passing a short buffer would make the native side read past its end, and
            // passing the hex text instead of the digest would silently hash the wrong
            // bytes. Both are caught here rather than in C.
            throw new IllegalArgumentException(
                    "previousHash must be " + HASH_LEN + " raw bytes (use SignedEntry.hash()), got "
                            + previousHash.length);
        }

        byte[] entryBytes = entryJson.getBytes(StandardCharsets.UTF_8);

        // entryJson is handed to JNA as a String, so its length must be counted with the
        // same encoding JNA will use, or the native side hashes a different span.
        requireUtf8StringEncoding();

        // prevMem is null for a chain's first entry; try-with-resources tolerates a null
        // resource (close is simply not called).
        try (Memory prevMem = previousHash == null ? null : new Memory(HASH_LEN);
                Memory hashOut = new Memory(HASH_LEN);
                Memory signatureOut = new Memory(SIG_LEN)) {

            if (prevMem != null) {
                prevMem.write(0, previousHash, 0, HASH_LEN);
            }

            // signatureLength is output-only: the native side overwrites it with the
            // number of bytes written and never reads the incoming value.
            LongByReference signatureLength = new LongByReference(SIG_LEN);

            int rc = library.resurs_audit_chain_entry(prevMem, entryJson, entryBytes.length,
                    hashOut, signatureOut, signatureLength);
            if (rc != ResursAuditLibrary.RESURS_AUDIT_OK) {
                throw new CryptoException(rc);
            }

            int written = (int) signatureLength.getValue();
            return new SignedEntry(
                    hashOut.getByteArray(0, HASH_LEN),
                    signatureOut.getByteArray(0, written));
        }
    }

    /**
     * Guards the one assumption JNA makes on our behalf: that a Java {@code String} maps
     * to the same bytes we counted with {@link StandardCharsets#UTF_8}. If they ever
     * diverge, the entry is hashed over a different span than the one recorded.
     */
    private static void requireUtf8StringEncoding() {
        String jnaEncoding = Native.getDefaultStringEncoding();
        if (!StandardCharsets.UTF_8.name().equalsIgnoreCase(jnaEncoding)) {
            throw new IllegalStateException(
                    "JNA string encoding is " + jnaEncoding + " but audit entries are hashed as UTF-8; "
                            + "start the JVM with -Djna.encoding=UTF-8");
        }
    }

    /**
     * Recomputes every link of a chain from the entries' actual content and verifies every
     * signature under {@code publicKey}.
     *
     * <p>The native side takes the entries and the lengths as flat, back-to-back buffers,
     * so both are packed here.
     *
     * @param hashes     the stored chain links, in chain order
     * @param signatures the stored signatures, in the same order
     * @param entries    the stored entry JSONs, in the same order
     * @param publicKey  the 32 raw bytes of the public key to verify under
     * @return {@code -1} if the whole chain verifies, otherwise the index of the first
     *         entry that fails either its link or its signature check
     * @throws CryptoException          if the native library reports a failure
     * @throws IllegalArgumentException if the lists do not describe one chain of the same
     *         length, or a value is not of the expected length
     */
    public int verifyChain(@NonNull List<byte[]> hashes, @NonNull List<byte[]> signatures,
            @NonNull List<String> entries, @NonNull byte[] publicKey) {
        AuditSigningService.requireVerifiableChain(hashes, signatures, entries, publicKey);
        int count = hashes.size();
        if (count == 0) {
            return VALID_CHAIN;
        }

        byte[][] entryBytes = new byte[count][];
        long entriesLen = 0;
        for (int i = 0; i < count; i++) {
            entryBytes[i] = entries.get(i).getBytes(StandardCharsets.UTF_8);
            entriesLen += entryBytes[i].length;
        }

        long signaturesLen = 0;
        for (byte[] signature : signatures) {
            signaturesLen += signature.length;
        }

        try (Memory hashesMem = allocate((long) count * HASH_LEN);
                Memory signaturesMem = allocate(signaturesLen);
                Memory signatureLensMem = allocate((long) count * SIZE_T_LEN);
                Memory entriesMem = allocate(entriesLen);
                Memory entryLensMem = allocate((long) count * SIZE_T_LEN);
                Memory publicKeyMem = new Memory(PUBKEY_LEN)) {

            long signatureOffset = 0;
            long entryOffset = 0;
            for (int i = 0; i < count; i++) {
                hashesMem.write(i * (long) HASH_LEN, hashes.get(i), 0, HASH_LEN);
                signatureLensMem.setLong(i * (long) SIZE_T_LEN, signatures.get(i).length);
                signaturesMem.write(signatureOffset, signatures.get(i), 0, signatures.get(i).length);
                signatureOffset += signatures.get(i).length;

                entryLensMem.setLong(i * (long) SIZE_T_LEN, entryBytes[i].length);
                entriesMem.write(entryOffset, entryBytes[i], 0, entryBytes[i].length);
                entryOffset += entryBytes[i].length;
            }

            publicKeyMem.write(0, publicKey, 0, PUBKEY_LEN);

            IntByReference firstInvalidIndex = new IntByReference(VALID_CHAIN);
            int rc = library.resurs_audit_verify_chain(hashesMem, signaturesMem, signatureLensMem,
                    entriesMem, entryLensMem, count, entriesLen, signaturesLen,
                    publicKeyMem, firstInvalidIndex);
            if (rc != ResursAuditLibrary.RESURS_AUDIT_OK) {
                throw new CryptoException(rc);
            }
            return firstInvalidIndex.getValue();
        }
    }

    /**
     * Allocates a native buffer of at least {@code length} bytes.
     *
     * <p>JNA rejects a zero-length allocation, so an empty entry JSON — legal, if unusual —
     * still gets one byte; the native side is told the real length and never reads it.
     */
    private static Memory allocate(long length) {
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Chain too large to verify in one call: " + length + " bytes");
        }
        return new Memory((int) Math.max(length, 1));
    }
}
