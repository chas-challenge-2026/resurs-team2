package se.comerit.resurs.api.v1.service;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * {@link AuditSigningService} backed by the native {@code resurs_audit} module, which
 * signs the hash chain with an Ed25519 key held outside the application.
 *
 * <p>All JNA work lives in {@link ResursAuditServiceImpl}; this class only adapts it to
 * the service interface, so callers can be written against {@link AuditSigningService}
 * without knowing whether the chain was signed by OpenSSL or by
 * {@link DummyAuditSigningService}.
 */
public class NativeAuditSigningService implements AuditSigningService {

    private final ResursAuditServiceImpl nativeSigner;

    public NativeAuditSigningService(ResursAuditServiceImpl nativeSigner) {
        this.nativeSigner = nativeSigner;
    }

    @Override
    @NonNull
    public SignedEntry signEntry(@NonNull String entryJson, @Nullable byte[] previousHash) {
        ResursAuditServiceImpl.SignedEntry signed = nativeSigner.signEntry(entryJson, previousHash);
        return new SignedEntry(signed.hash(), signed.signature());
    }

    @Override
    public int verifyChain(@NonNull List<byte[]> hashes, @NonNull List<byte[]> signatures,
            @NonNull List<String> entries, @NonNull byte[] publicKey) {
        return nativeSigner.verifyChain(hashes, signatures, entries, publicKey);
    }
}
