package se.comerit.resurs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import se.comerit.resurs.api.v1.service.AuditSigningService;

/**
 * The loader turns the PEM file {@code infra/Makefile keys-audit} writes into the raw
 * key {@code resurs_audit_verify_chain} expects, and refuses anything else at startup.
 */
class AuditPublicKeyLoaderTest {

    @TempDir
    Path dir;

    @Test
    void loadsRawEd25519KeyFromPem() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] expected = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded())
                .getPublicKeyData().getOctets();
        Path file = writePem("PUBLIC KEY", keyPair.getPublic().getEncoded());

        AuditPublicKeyLoader loader = new AuditPublicKeyLoader(file.toString());

        assertThat(loader.publicKey()).hasValueSatisfying(key -> {
            assertThat(key).hasSize(AuditSigningService.PUBLIC_KEY_LEN);
            assertThat(key).isEqualTo(expected);
        });
    }

    @Test
    void blankPathLeavesVerificationUnavailable() {
        assertThat(new AuditPublicKeyLoader("").publicKey()).isEmpty();
        assertThat(new AuditPublicKeyLoader("   ").publicKey()).isEmpty();
    }

    @Test
    void returnedKeyIsACopy() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        AuditPublicKeyLoader loader = new AuditPublicKeyLoader(
                writePem("PUBLIC KEY", keyPair.getPublic().getEncoded()).toString());

        loader.publicKey().orElseThrow()[0] ^= 0x01;

        assertThat(loader.publicKey().orElseThrow()).isEqualTo(
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded())
                        .getPublicKeyData().getOctets());
    }

    @Test
    void rejectsNonEd25519Key() throws Exception {
        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp256r1"));
        Path file = writePem("PUBLIC KEY", ec.generateKeyPair().getPublic().getEncoded());

        assertThatThrownBy(() -> new AuditPublicKeyLoader(file.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not Ed25519");
    }

    @Test
    void rejectsPrivateKeyPem() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path file = writePem("PRIVATE KEY", keyPair.getPrivate().getEncoded());

        assertThatThrownBy(() -> new AuditPublicKeyLoader(file.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a PEM PUBLIC KEY");
    }

    @Test
    void rejectsGarbageFile() throws Exception {
        Path file = Files.writeString(dir.resolve("garbage.pub"), "not a key at all\n");

        assertThatThrownBy(() -> new AuditPublicKeyLoader(file.toString()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedKeyBody() throws Exception {
        Path file = writePem("PUBLIC KEY", new byte[] {1, 2, 3, 4});

        assertThatThrownBy(() -> new AuditPublicKeyLoader(file.toString()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMissingFile() {
        Path missing = dir.resolve("missing.pub");

        assertThatThrownBy(() -> new AuditPublicKeyLoader(missing.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot read");
    }

    private Path writePem(String type, byte[] der) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        String pem = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
        return Files.writeString(dir.resolve("key.pem"), pem, StandardCharsets.US_ASCII);
    }
}
