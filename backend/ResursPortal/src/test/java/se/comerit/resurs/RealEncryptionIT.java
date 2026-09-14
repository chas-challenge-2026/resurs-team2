package se.comerit.resurs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import se.comerit.resurs.api.v1.service.ResursCryptoService;
import se.comerit.resurs.api.v1.service.ResursCryptoServiceImpl;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.exception.CryptoException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;
import se.comerit.resurs.repository.CompanyRepository;

/**
 * Full-stack encryption test against the real native module.
 *
 * <p>Runs under the {@code encryptiontest} profile (not {@code test}), so the
 * real {@link ResursCryptoServiceImpl} + {@code EncryptedPiiCodec} are active
 * instead of the test doubles. Requires {@code libresurs_crypto.so} on the JNA
 * library path (built by {@code make build-native}) and a 64-byte key file
 * (generated into a temp dir here, never committed).</p>
 *
 * <p>The class name ends in {@code IT} (not {@code Test}) so the default
 * Surefire run of {@code ./mvnw test} skips it; run it explicitly with
 * {@code ./mvnw -Dtest=RealEncryptionIT test}.</p>
 */
@SpringBootTest
@ActiveProfiles({"v2", "encryptiontest"})
class RealEncryptionIT {

    private static final int NONCE_LEN = 12;
    private static final int KEY_VERSION_LEN = 1;
    private static final int TAG_LEN = 16;

    private static final String ORG_NUMBER = "556000-4321";
    private static final String COMPANY_NAME = "Malmö Test AB";
    private static final String SIGNATORY = "Olle Öberg";

    private static final String LOOKUP_ORG_NUMBER = "556000-5555";

    private static final Path KEY_FILE = writeTempKeyFile();

    @DynamicPropertySource
    static void cryptoProperties(DynamicPropertyRegistry registry) {
        registry.add("resurs.jna.key.path", KEY_FILE::toString);
    }

    @AfterAll
    static void cleanupKeyFile() {
        try {
            Files.deleteIfExists(KEY_FILE);
            Path dir = KEY_FILE.getParent();
            if (dir != null) {
                Files.deleteIfExists(dir);
            }
        } catch (IOException _) {
            // best-effort cleanup; leave it to the OS temp-manager if this fails
        }
    }

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ResursCryptoService cryptoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void realCryptoLibraryIsActiveNotTheDummyFallback() {
        assertThat(cryptoService).isInstanceOf(ResursCryptoServiceImpl.class);
    }

    @Test
    void piiColumnsAreOpaqueCiphertextAtRest() throws Exception {
        Company saved = companyRepository.save(new Company(ORG_NUMBER, COMPANY_NAME, SIGNATORY));
        Long id = saved.getId();

        String storedOrg = jdbcTemplate.queryForObject(
                "SELECT org_number FROM companies WHERE id = ?", String.class, id);
        String storedName = jdbcTemplate.queryForObject(
                "SELECT company_name FROM companies WHERE id = ?", String.class, id);
        String storedSignatory = jdbcTemplate.queryForObject(
                "SELECT authorized_signatory FROM companies WHERE id = ?", String.class, id);
        byte[] storedIndex = jdbcTemplate.queryForObject(
                "SELECT org_number_index FROM companies WHERE id = ?", byte[].class, id);

        // 1. Not plaintext, and 2. exactly [12 nonce][1 key version][10 plaintext][16 tag]
        assertThat(storedOrg).isNotEqualTo(ORG_NUMBER);
        byte[] orgBlob = Base64.getDecoder().decode(storedOrg);
        assertThat(orgBlob).hasSize(NONCE_LEN + KEY_VERSION_LEN + ORG_NUMBER.length() + TAG_LEN);
        assertThat(orgBlob[NONCE_LEN]).isEqualTo((byte) 1);
        assertThat(storedName).isNotEqualTo(COMPANY_NAME);
        assertThat(storedSignatory).isNotEqualTo(SIGNATORY);

        // 3. The stored values decrypt back to the original plaintext (AES-256-GCM, real key)
        assertThat(cryptoService.decryptPii(orgBlob)).isEqualTo(ORG_NUMBER);
        assertThat(cryptoService.decryptPii(Base64.getDecoder().decode(storedName))).isEqualTo(COMPANY_NAME);
        assertThat(cryptoService.decryptPii(Base64.getDecoder().decode(storedSignatory))).isEqualTo(SIGNATORY);

        // 4. The blind index is a 32-byte keyed HMAC, not the naive SHA-256 of the plaintext
        assertThat(storedIndex).hasSize(32);
        byte[] naiveSha256 = MessageDigest.getInstance("SHA-256")
                .digest(ORG_NUMBER.trim().replaceAll("\\s+", " ")
                        .toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        assertThat(storedIndex).isNotEqualTo(naiveSha256);
    }

    @Test
    void lookupByOrgNumberStillWorksThroughKeyedHmacBlindIndex() {
        companyRepository.save(new Company(LOOKUP_ORG_NUMBER, COMPANY_NAME, SIGNATORY));

        Company found = companyRepository.findByOrgNumber(LOOKUP_ORG_NUMBER).orElseThrow();
        assertThat(found.getName()).isEqualTo(COMPANY_NAME);
        assertThat(found.getAuthorizedSignatory()).isEqualTo(SIGNATORY);
    }

    @Test
    void eachEncryptionUsesARandomNonce() {
        byte[] first = cryptoService.encryptPii(ORG_NUMBER);
        byte[] second = cryptoService.encryptPii(ORG_NUMBER);

        assertThat(first).isNotEqualTo(second);
        assertThat(Arrays.copyOfRange(first, 0, NONCE_LEN))
                .isNotEqualTo(Arrays.copyOfRange(second, 0, NONCE_LEN));
        assertThat(cryptoService.decryptPii(first)).isEqualTo(ORG_NUMBER);
        assertThat(cryptoService.decryptPii(second)).isEqualTo(ORG_NUMBER);
    }

    @Test
    void ciphertextTamperingIsRejectedByGcmTag() {
        byte[] blob = cryptoService.encryptPii("tamper detection");
        byte[] tampered = blob.clone();
        tampered[tampered.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cryptoService.decryptPii(tampered))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void seededDemoCompanyIsStoredEncrypted() {
        Company seeded = companyRepository.findByOrgNumber("556000-1234").orElseThrow();
        assertThat(seeded.getName()).isEqualTo("Malmö Fastigheter AB");

        String storedOrg = jdbcTemplate.queryForObject(
                "SELECT org_number FROM companies WHERE org_number_index = ?",
                String.class, seeded.getOrgNumberIndex());
        assertThat(storedOrg).isNotEqualTo("556000-1234");
        assertThat(cryptoService.decryptPii(Base64.getDecoder().decode(storedOrg)))
                .isEqualTo("556000-1234");
    }

    @Test
    void auditLogEntryIsStoredEncryptedAtRest() {
        Company company = companyRepository.save(
                new Company("556000-7777", "Audit Log AB", "Kalle Test"));
        Application application = applicationRepository.save(
                new Application(company, new BigDecimal("250000.00"), "Rörelsekapital"));

        String plaintext = "{\"action\":\"APPLICATION_CREATED\",\"orgNumber\":\"556000-7777\"}";
        AuditLog log = auditLogRepository.save(new AuditLog(application, 1L, "", "", plaintext));

        String stored = jdbcTemplate.queryForObject(
                "SELECT entry FROM audit_log WHERE application_id = ? AND sequence_number = ?",
                String.class, application.getId(), log.getSequenceNumber());

        // 1. Not plaintext, and 2. exactly [12 nonce][1 key version][N plaintext][16 tag]
        assertThat(stored).isNotEqualTo(plaintext);
        byte[] blob = Base64.getDecoder().decode(stored);
        assertThat(blob).hasSize(NONCE_LEN + KEY_VERSION_LEN + plaintext.length() + TAG_LEN);
        assertThat(blob[NONCE_LEN]).isEqualTo((byte) 1);

        // 3. Decrypts back to the original audit entry payload (AES-256-GCM, real key)
        assertThat(cryptoService.decryptPii(blob)).isEqualTo(plaintext);
    }

    private static Path writeTempKeyFile() {
        try {
            Path dir = Files.createTempDirectory("resurs-crypto-it");
            byte[] key = new byte[64];
            new SecureRandom().nextBytes(key);
            Path file = dir.resolve("crypto.key");
            Files.write(file, key);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create crypto test key", e);
        }
    }
}