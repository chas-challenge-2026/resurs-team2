package se.comerit.resurs.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import se.comerit.resurs.api.v1.service.AuditSigningService;

/**
 * Loads the Ed25519 public key that audit hash chains are verified against.
 *
 * <p>The key is read from a PEM {@code PUBLIC KEY} file (SubjectPublicKeyInfo), the
 * format {@code infra/Makefile keys-audit} writes to {@code secrets/resurs-audit.pub}.
 * It is configured like the private key: property, environment variable, Docker secret.
 * Unlike the private key it never goes to the native module at startup, because
 * {@code resurs_audit_verify_chain} takes the public key from the caller on every call.
 *
 * <p>A blank path leaves verification unavailable. A path that is set but unusable
 * fails startup, the same way a broken private key does.
 */
@Component
public class AuditPublicKeyLoader {

    private static final Logger log = LoggerFactory.getLogger(AuditPublicKeyLoader.class);

    /** id-Ed25519 from RFC 8410. */
    static final ASN1ObjectIdentifier ED25519 = new ASN1ObjectIdentifier("1.3.101.112");

    private static final String PEM_TYPE = "PUBLIC KEY";

    private final byte[] publicKey;

    public AuditPublicKeyLoader(@Value("${resurs.audit.public-key.path:}") String path) {
        if (path == null || path.isBlank()) {
            log.warn("Audit chain verification DISABLED: no resurs.audit.public-key.path configured");
            this.publicKey = null;
            return;
        }
        this.publicKey = load(Path.of(path));
        log.info("Audit public key loaded, path={}", path);
    }

    /** The raw 32-byte Ed25519 public key, or empty when no path is configured. */
    public Optional<byte[]> publicKey() {
        return Optional.ofNullable(publicKey).map(byte[]::clone);
    }

    static byte[] load(Path path) {
        PemObject pem;
        try (Reader in = Files.newBufferedReader(path, StandardCharsets.US_ASCII);
                PemReader reader = new PemReader(in)) {
            pem = reader.readPemObject();
        } catch (IOException | IllegalStateException e) {
            throw new IllegalStateException("Cannot read audit public key: " + path, e);
        }
        if (pem == null || !PEM_TYPE.equals(pem.getType())) {
            throw new IllegalStateException("Audit public key is not a PEM PUBLIC KEY: " + path);
        }

        byte[] raw;
        try {
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(pem.getContent());
            if (!ED25519.equals(spki.getAlgorithm().getAlgorithm())) {
                throw new IllegalStateException("Audit public key is not Ed25519: " + path);
            }
            raw = spki.getPublicKeyData().getOctets();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Malformed audit public key: " + path, e);
        }

        if (raw.length != AuditSigningService.PUBLIC_KEY_LEN) {
            throw new IllegalStateException("Audit public key must be "
                    + AuditSigningService.PUBLIC_KEY_LEN + " bytes, got " + raw.length + ": " + path);
        }
        return raw;
    }
}
