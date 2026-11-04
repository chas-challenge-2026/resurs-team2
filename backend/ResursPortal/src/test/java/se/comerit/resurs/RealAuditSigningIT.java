package se.comerit.resurs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.jna.Native;

import se.comerit.resurs.api.v1.service.AuditSigningService;
import se.comerit.resurs.api.v1.service.DummyAuditSigningService;
import se.comerit.resurs.api.v1.service.NativeAuditSigningService;
import se.comerit.resurs.api.v1.service.ResursAuditServiceImpl;
import se.comerit.resurs.api.v1.service.AuditSigningService.SignedEntry;
import se.comerit.resurs.config.ResursAuditLibrary;

/**
 * Audit chain signing against the real native module, exercising the JNA buffer
 * packing of {@link ResursAuditServiceImpl#verifyChain} against real Ed25519 keys.
 *
 * <p>Requires {@code libresurs_audit.so} (built by {@code make build-native}); the test
 * is skipped when the library has not been built. The signing key is generated into a
 * temp dir here, never committed, and the module path is a constant in this file rather
 * than Spring's, so no application context is started.
 *
 * <p>The class name ends in {@code IT} (not {@code Test}) so the default Surefire run of
 * {@code ./mvnw test} skips it; run it explicitly with
 * {@code ./mvnw -Dtest=RealAuditSigningIT test}.</p>
 */
class RealAuditSigningIT {

    private static final String ENTRY_1 = "{\"action\":\"APPLICATION_CREATED\",\"orgNumber\":\"556000-1234\"}";
    /** Non-ASCII content: the packed entry lengths are UTF-8 byte counts, not char counts. */
    private static final String ENTRY_2 = "{\"action\":\"MANUAL_DECISION\",\"note\":\"Rörelsekapital — godkänt\"}";
    private static final String ENTRY_3 = "{\"action\":\"ETA_SET\",\"businessDays\":2}";

    private static final List<Path> LIBRARY_CANDIDATES = List.of(
            Path.of("..", "..", "native", "build", "audit", "libresurs_audit.so"),
            Path.of("..", "..", "target", "libs", "libresurs_audit.so"));

    private static Path library;
    private static Path keyFile;
    private static byte[] publicKey;
    private static ResursAuditLibrary nativeLibrary;
    private static AuditSigningService signer;

    private final List<byte[]> hashes = new ArrayList<>();
    private final List<byte[]> signatures = new ArrayList<>();
    private final List<String> entries = new ArrayList<>();

    @BeforeAll
    static void loadNativeLibraryAndInitKey() throws Exception {
        // Read the first time the service asks for it, before Native caches an encoding.
        System.setProperty("jna.encoding", "UTF-8");

        library = LIBRARY_CANDIDATES.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
        Assumptions.assumeTrue(library != null,
                "libresurs_audit.so not built - run 'make build-native'");

        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        publicKey = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded())
                .getPublicKeyData().getBytes();
        assertThat(publicKey).hasSize(AuditSigningService.PUBLIC_KEY_LEN);
        keyFile = writePemKey(keyPair);

        nativeLibrary = Native.load(library.toString(), ResursAuditLibrary.class);
        assertThat(nativeLibrary.resurs_audit_init(keyFile.toString()))
                .isEqualTo(ResursAuditLibrary.RESURS_AUDIT_OK);

        signer = new NativeAuditSigningService(new ResursAuditServiceImpl(nativeLibrary));
    }

    @AfterAll
    static void shutdown() throws IOException {
        if (nativeLibrary != null) {
            nativeLibrary.resurs_audit_shutdown();
        }
        if (keyFile != null) {
            Files.deleteIfExists(keyFile);
            Files.deleteIfExists(keyFile.getParent());
        }
    }

    @Test
    void anUntouchedChainVerifiesUnderTheRealKey() {
        signChain();

        assertThat(signer.verifyChain(hashes, signatures, entries, publicKey))
                .isEqualTo(AuditSigningService.VALID_CHAIN);
    }

    @Test
    void theChainLinkIsTheSameOneTheDummyComputes() {
        signChain();

        // The dummy mirrors the native formula, so switching implementations does not
        // change the links a stored chain is made of.
        AuditSigningService dummy = new DummyAuditSigningService();
        byte[] previousHash = null;
        for (int i = 0; i < entries.size(); i++) {
            SignedEntry dummyEntry = dummy.signEntry(entries.get(i), previousHash);
            assertThat(dummyEntry.hash()).isEqualTo(hashes.get(i));
            previousHash = hashes.get(i);
        }
    }

    @Test
    void editedEntryContentIsDetectedAtThatEntry() {
        signChain();
        entries.set(1, "{\"action\":\"MANUAL_DECISION\",\"note\":\"Rörelsekapital — avslagen\"}");

        assertThat(signer.verifyChain(hashes, signatures, entries, publicKey)).isEqualTo(1);
    }

    @Test
    void reorderedEntriesAreDetectedAtTheFirstMovedEntry() {
        signChain();
        Collections.swap(entries, 0, 1);

        assertThat(signer.verifyChain(hashes, signatures, entries, publicKey)).isZero();
    }

    @Test
    void anEditedStoredSignatureIsDetected() {
        signChain();
        signatures.set(2, flipLastBit(signatures.get(2)));

        assertThat(signer.verifyChain(hashes, signatures, entries, publicKey)).isEqualTo(2);
    }

    @Test
    void verifyingUnderAnotherPublicKeyFailsAtTheFirstEntry() {
        signChain();
        byte[] otherKey = new byte[AuditSigningService.PUBLIC_KEY_LEN];

        assertThat(signer.verifyChain(hashes, signatures, entries, otherKey)).isZero();
    }

    private void signChain() {
        hashes.clear();
        signatures.clear();
        entries.clear();

        byte[] previousHash = null;
        for (String entry : List.of(ENTRY_1, ENTRY_2, ENTRY_3)) {
            SignedEntry signed = signer.signEntry(entry, previousHash);
            hashes.add(signed.hash());
            signatures.add(signed.signature());
            entries.add(entry);
            previousHash = signed.hash();
        }
    }

    private static Path writePemKey(KeyPair keyPair) throws IOException {
        Path file = Files.createTempDirectory("resurs-audit-it").resolve("audit.key");
        // The same PKCS#8 PEM 'openssl genpkey -algorithm ED25519' writes, which is what
        // resurs_audit_init expects.
        String base64 = Base64.getMimeEncoder(64, new byte[] { '\n' })
                .encodeToString(keyPair.getPrivate().getEncoded());
        Files.writeString(file, "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n");
        return file;
    }

    private static byte[] flipLastBit(byte[] value) {
        byte[] copy = value.clone();
        copy[copy.length - 1] ^= 0x01;
        return copy;
    }
}
