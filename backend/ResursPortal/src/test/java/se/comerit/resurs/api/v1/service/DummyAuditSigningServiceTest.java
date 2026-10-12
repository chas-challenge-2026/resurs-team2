package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import se.comerit.resurs.api.v1.service.AuditSigningService.SignedEntry;

/**
 * The test stand-in only has to keep the code around it working: return values of the
 * documented shape, chain them, and notice when a chain no longer lines up. The
 * signatures themselves are worthless, and the real behaviour is covered by
 * {@code RealAuditSigningIT} against the native module.
 */
class DummyAuditSigningServiceTest {

    private static final List<String> ENTRIES = List.of(
            "{\"action\":\"APPLICATION_CREATED\"}",
            "{\"action\":\"SCORING_RUN\",\"score\":42}",
            "{\"action\":\"MANUAL_DECISION\",\"approved\":true}");

    private final DummyAuditSigningService signer = new DummyAuditSigningService();
    private final List<byte[]> hashes = new ArrayList<>();
    private final List<byte[]> signatures = new ArrayList<>();
    private final List<String> entries = new ArrayList<>(ENTRIES);

    @Test
    void signedValuesHaveTheDocumentedShape() {
        SignedEntry signed = signer.signEntry(ENTRIES.get(0), null);

        assertThat(signed.hash()).hasSize(AuditSigningService.HASH_LEN);
        assertThat(signed.signature()).hasSize(AuditSigningService.SIGNATURE_LEN);
        assertThat(signed.hashHex()).hasSize(2 * AuditSigningService.HASH_LEN);
    }

    @Test
    void aChainLinksEachEntryToTheOneBeforeIt() {
        signChain();

        assertThat(signer.verifyChain(hashes, signatures, entries, new byte[AuditSigningService.PUBLIC_KEY_LEN]))
                .isEqualTo(AuditSigningService.VALID_CHAIN);
        // Re-signing an entry on its own gives a different link than mid-chain, so an
        // entry cannot be lifted out of the chain and replayed elsewhere.
        assertThat(signer.signEntry(ENTRIES.get(1), null).hash())
                .isNotEqualTo(signer.signEntry(ENTRIES.get(1), hashes.get(0)).hash());
    }

    @Test
    void editedContentIsDetectedAtThatEntry() {
        signChain();
        entries.set(1, "{\"action\":\"SCORING_RUN\",\"score\":99}");

        assertThat(signer.verifyChain(hashes, signatures, entries, new byte[AuditSigningService.PUBLIC_KEY_LEN]))
                .isEqualTo(1);
    }

    @Test
    void reorderedEntriesAreDetectedAtTheFirstMovedEntry() {
        signChain();
        Collections.swap(entries, 1, 2);

        assertThat(signer.verifyChain(hashes, signatures, entries, new byte[AuditSigningService.PUBLIC_KEY_LEN]))
                .isEqualTo(1);
    }

    @Test
    void signingIsReproducibleSoAChainSignedInOneRunVerifiesInTheNext() {
        signChain();

        assertThat(signer.signEntry(ENTRIES.get(0), null).signature()).isEqualTo(signatures.get(0));
    }

    @Test
    void valuesOfTheWrongLengthAreRejected() {
        assertThatThrownBy(() -> signer.signEntry(ENTRIES.get(0), new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");

        signChain();
        assertThatThrownBy(() -> signer.verifyChain(
                hashes, signatures, entries, new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publicKey");
    }

    private void signChain() {
        hashes.clear();
        signatures.clear();

        byte[] previousHash = null;
        for (String entry : ENTRIES) {
            SignedEntry signed = signer.signEntry(entry, previousHash);
            hashes.add(signed.hash());
            signatures.add(signed.signature());
            previousHash = signed.hash();
        }
    }
}
