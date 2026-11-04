package se.comerit.resurs.api.v1.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Test stand-in for {@link AuditSigningService}, so the code around it can be exercised
 * without the native audit module.
 *
 * <p>Only enough is faked to keep that code working. {@link #signEntry} returns a
 * well-formed {@link SignedEntry}: a real {@code SHA-256(previousHash || entryJson)} link,
 * so a stored chain is still a chain, plus a stand-in value of the signature length that
 * is only ever compared against another stand-in value. There is no key pair, so
 * {@link #verifyChain} ignores the public key: the signatures are worthless, and a chain
 * that passes here proves nothing.
 */
@Service
@Profile("test")
public class DummyAuditSigningService implements AuditSigningService {

    private static final String SHA_256 = "SHA-256";
    private static final String SHA_512 = "SHA-512";

    /** Keeps the stand-in signature distinct from the link it covers. */
    private static final byte[] SIGNATURE_MARK = "resurs-dummy-signature".getBytes(StandardCharsets.UTF_8);

    @Override
    @NonNull
    public SignedEntry signEntry(@NonNull String entryJson, @Nullable byte[] previousHash) {
        byte[] link = chainLink(previousHash, entryJson);
        return new SignedEntry(link, standInSignature(link));
    }

    @Override
    public int verifyChain(@NonNull List<byte[]> hashes, @NonNull List<byte[]> signatures,
            @NonNull List<String> entries, @NonNull byte[] publicKey) {
        AuditSigningService.requireVerifiableChain(hashes, signatures, entries, publicKey);

        byte[] previousHash = null;
        for (int i = 0; i < hashes.size(); i++) {
            byte[] link = chainLink(previousHash, entries.get(i));
            if (!MessageDigest.isEqual(link, hashes.get(i))
                    || !MessageDigest.isEqual(standInSignature(link), signatures.get(i))) {
                return i;
            }
            previousHash = hashes.get(i);
        }
        return VALID_CHAIN;
    }

    /** The chain link for one entry, computed exactly as the native module does. */
    private static byte[] chainLink(@Nullable byte[] previousHash, @NonNull String entryJson) {
        MessageDigest sha256 = digest(SHA_256);
        if (previousHash == null) {
            sha256.update(new byte[HASH_LEN]); // the first entry of a chain links to zeros
        } else {
            if (previousHash.length != HASH_LEN) {
                throw new IllegalArgumentException(
                        "previousHash must be " + HASH_LEN + " raw bytes (use SignedEntry.hash()), got "
                                + previousHash.length);
            }
            sha256.update(previousHash);
        }
        sha256.update(entryJson.getBytes(StandardCharsets.UTF_8));
        return sha256.digest();
    }

    /** SHA-512 over the link: 64 bytes, reproducible, and not a signature. */
    private static byte[] standInSignature(byte[] link) {
        MessageDigest sha512 = digest(SHA_512);
        sha512.update(link);
        sha512.update(SIGNATURE_MARK);
        return sha512.digest();
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " unavailable", e);
        }
    }
}
