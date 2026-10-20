package se.comerit.resurs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import se.comerit.resurs.api.v1.service.AuditSigningService;
import se.comerit.resurs.config.PiiInitializer;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;

/**
 * The seeded audit entries are signed at startup, not left as the unsigned placeholders
 * {@code data.sql} inserts.
 *
 * <p>Runs under the {@code encryptiontest} profile rather than {@code test} because
 * {@code PiiInitializer} is excluded from the test profile -- it is the component under
 * test here. {@code data.sql} is loaded explicitly because the test properties blank out
 * the data locations, and the unsigned rows it inserts are the input to the pass being
 * covered; a run without them would only exercise the insert-into-an-empty-chain path.
 * The entries end up encrypted at rest, so the entity is what decodes them back to the
 * plaintext the signature covers.</p>
 *
 * <p>Verification uses a stand-in public key: the signer here is the dummy one (the
 * native library is a separate concern, see {@link RealAuditSigningIT}), and what
 * matters is that a whole chain of linked, correctly sized links came out the other
 * end. The class name ends in {@code IT} so the default Surefire run of
 * {@code ./mvnw test} skips it; run it with {@code ./mvnw -Dtest=SeedAuditSigningIT test}.</p>
 */
@SpringBootTest(properties = "spring.sql.init.data-locations=classpath:data.sql")
@ActiveProfiles({ "v2", "encryptiontest" })
class SeedAuditSigningIT {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private AuditSigningService auditSigningService;

    @Autowired
    private PiiInitializer piiInitializer;

    @Test
    void everySeededChainIsSignedAndVerifies() {
        List<List<AuditLog>> chains = seededChains();

        // data.sql seeds one application with two entries; without them there is nothing
        // here and the assertions below would pass on an empty list.
        assertThat(chains).isNotEmpty();

        assertThat(chains).allSatisfy(chain -> {
            assertThat(chain).allSatisfy(entry -> {
                assertThat(entry.getHash()).hasSize(AuditSigningService.HASH_LEN);
                assertThat(entry.getSignature()).hasSize(AuditSigningService.SIGNATURE_LEN);
            });

            assertThat(auditSigningService.verifyChain(
                    chain.stream().map(AuditLog::getHash).toList(),
                    chain.stream().map(AuditLog::getSignature).toList(),
                    chain.stream().map(AuditLog::getEntry).toList(),
                    new byte[AuditSigningService.PUBLIC_KEY_LEN]))
                    .as("chain %s", chain.stream().map(AuditLog::getSequenceNumber).toList())
                    .isEqualTo(AuditSigningService.VALID_CHAIN);
        });
    }

    @Test
    void aSecondStartLeavesTheChainExactlyAsItIs() {
        List<String> before = describe(seededChains());

        piiInitializer.run(null);

        // The encryption and signing passes are both keyed on work left to do -- a
        // plaintext entry, a missing signature -- so a restart finds neither and writes
        // nothing. Re-encrypting or re-signing on every boot would leave the chain
        // rewritten on each deploy, which is exactly what an audit log must not do.
        assertThat(describe(seededChains())).isEqualTo(before);
    }

    private List<List<AuditLog>> seededChains() {
        return applicationRepository.findAll().stream()
                .map(application -> auditLogRepository.findByApplication(application,
                        Sort.by("sequenceNumber")))
                .filter(chain -> !chain.isEmpty())
                .toList();
    }

    /** Every stored field of every entry, so a rewrite cannot hide behind equal hashes. */
    private static List<String> describe(List<List<AuditLog>> chains) {
        return chains.stream()
                .flatMap(List::stream)
                .map(entry -> entry.getSequenceNumber()
                        + ":" + HexFormat.of().formatHex(entry.getHash())
                        + ":" + HexFormat.of().formatHex(entry.getSignature())
                        + ":" + entry.getEntry())
                .toList();
    }
}
