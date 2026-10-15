package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import se.comerit.resurs.api.v1.service.AuditChainVerifier.Report;
import se.comerit.resurs.api.v1.service.AuditSigningService.SignedEntry;
import se.comerit.resurs.config.AuditPublicKeyLoader;
import se.comerit.resurs.config.PiiCodec;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;

/**
 * Measures full audit chain verification with the real native modules: Ed25519 signatures
 * from {@code libresurs_audit.so} and AES-GCM encrypted entries from
 * {@code libresurs_crypto.so}, on many short per-application chains.
 *
 * <p>Prints the time to read and decrypt every entry on one thread, the time for a full
 * {@link AuditChainVerifier#verifyAll()} on one thread without a pool, and on pools sized to
 * this machine (see {@link #poolSizes()}). A pool run uses up to one more thread than its
 * size: under backpressure the
 * submitting thread runs a batch itself. Nothing is asserted about timings, which depend on
 * the machine; only that every chain verifies.
 *
 * <p>Size: {@code -Daudit.perf.applications} (default 50 000) applications of
 * {@code -Daudit.perf.entries} (default 10) entries each. Seeding signs and encrypts every
 * entry, so it takes a while on its own.
 *
 * <p>Runs only with {@code -Daudit.perf=true}, so a run of every test (in Maven or an IDE)
 * skips it; it is also tagged {@code benchmark} for IDE and Maven tag filters. Run it with
 * {@code make bench-audit}. It lives in this package to shut its verifier pools down.
 */
@SpringBootTest
@ActiveProfiles({"v2", "encryptiontest"})
@Tag("benchmark")
@DisplayName("Audit verification benchmark (run: make bench-audit)")
class AuditVerifyPerfIT {

    private static final int APPLICATIONS = Integer.getInteger("audit.perf.applications", 50_000);
    private static final int ENTRIES = Integer.getInteger("audit.perf.entries", 10);
    private static final int PROCESSORS = Runtime.getRuntime().availableProcessors();
    private static final int[] POOL_SIZES = poolSizes();
    /** Runs per mode, -Daudit.perf.repeats; above 1 the report shows the median and spread. */
    private static final int REPEATS = Math.max(1, Integer.getInteger("audit.perf.repeats", 1));
    private static final int BATCH_SIZE = 500;
    /** Same default as resurs.audit.verify.db-reads; override with -Daudit.perf.db-reads. */
    private static final int DB_READS = Integer.getInteger("audit.perf.db-reads", 2);

    /** Ids well clear of the seed data that PiiInitializer creates. */
    private static final long COMPANY_ID = 900_000;
    private static final long FIRST_APPLICATION_ID = 1_000_000;
    private static final int INSERT_CHUNK = 5_000;

    private static final Path LIBS = Path.of("..", "..", "target", "libs").toAbsolutePath().normalize();
    private static final Path KEY_DIR = createKeys();

    static {
        // Set before the Spring context loads the native modules and JNA caches an encoding;
        // the audit signer refuses to sign under a non-UTF-8 JNA encoding.
        System.setProperty("jna.encoding", "UTF-8");
    }

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) {
        registry.add("resurs.jna.key.path", () -> KEY_DIR.resolve("crypto.key").toString());
        registry.add("resurs.audit.key.path", () -> KEY_DIR.resolve("audit.key").toString());
        registry.add("resurs.audit.public-key.path", () -> KEY_DIR.resolve("audit.pub").toString());
    }

    @BeforeAll
    static void requireBenchmarkRunAndNativeLibraries() {
        // Printed as well as passed to the assumption: IDE runners show only an "ignored"
        // mark for a skipped class, and Surefire drops a class-level assumption's message.
        if (!Boolean.getBoolean("audit.perf")) {
            String hint = "AuditVerifyPerfIT is a benchmark, skipped in normal test runs. Run it with: make bench-audit";
            System.out.println(hint);
            Assumptions.abort(hint);
        }
        Assumptions.assumeTrue(Files.isRegularFile(LIBS.resolve("libresurs_audit.so"))
                && Files.isRegularFile(LIBS.resolve("libresurs_crypto.so")),
                "native libraries not built in " + LIBS + " - run 'make build-native'");
    }

    @Autowired
    private AuditSigningService signer;

    @Autowired
    private AuditPublicKeyLoader publicKeyLoader;

    @Autowired
    private PiiCodec piiCodec;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void measureFullVerification() {
        assertThat(signer).isInstanceOf(NativeAuditSigningService.class);
        assertThat(piiCodec.getClass().getSimpleName()).isEqualTo("EncryptedPiiCodec");

        long seedMs = seed();
        long totalEntries = (long) APPLICATIONS * ENTRIES;

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"seed (sign + encrypt + insert)", formatMs(seedMs), "", "", ""});

        long loadMs = loadAndDecryptOnly();
        rows.add(new String[] {"read + decrypt only, 1 thread", formatMs(loadMs), "",
                perSecondText(totalEntries, loadMs), ""});

        // Mode 0 is the single-thread baseline (every batch on the calling thread); the
        // others are the pools. Each repeat rotates the order, so every mode runs early and
        // late, and warming, heating and GC state do not always favour the same one.
        List<Integer> modes = new ArrayList<>();
        modes.add(0);
        for (int threads : POOL_SIZES) {
            modes.add(threads);
        }
        Map<Integer, List<Long>> times = new LinkedHashMap<>();
        for (int mode : modes) {
            times.put(mode, new ArrayList<>());
        }
        List<String> orders = new ArrayList<>();
        for (int r = 0; r < REPEATS; r++) {
            List<Integer> order = new ArrayList<>(modes);
            Collections.rotate(order, -(r % modes.size()));
            orders.add(order.stream().map(m -> m == 0 ? "1" : String.valueOf(m))
                    .collect(Collectors.joining("->")));
            for (int mode : order) {
                times.get(mode).add(measure(mode, totalEntries));
            }
        }

        long singleThreadMs = median(times.get(0));
        rows.add(new String[] {"verifyAll, 1 thread (no pool)", formatMs(singleThreadMs), spread(times.get(0)),
                perSecondText(totalEntries, singleThreadMs), "x1.0"});

        long bestMs = singleThreadMs;
        int bestPool = 1;
        for (int threads : POOL_SIZES) {
            long ms = median(times.get(threads));
            // CallerRunsPolicy: when the queue is full, the submitting thread runs a batch too,
            // so up to threads + 1 threads verify at once.
            rows.add(new String[] {"verifyAll, pool of " + threads + " (+ caller)", formatMs(ms),
                    spread(times.get(threads)), perSecondText(totalEntries, ms),
                    String.format(Locale.ROOT, "x%.1f", (double) singleThreadMs / Math.max(ms, 1))});
            if (ms < bestMs) {
                bestMs = ms;
                bestPool = threads;
            }
        }

        boolean repeated = REPEATS > 1;
        String title = String.format(Locale.ROOT,
                "AUDIT VERIFY BENCHMARK  %,d applications x %d entries = %,d entries, %d processors, db-reads %d",
                APPLICATIONS, ENTRIES, totalEntries, PROCESSORS, DB_READS)
                + (repeated ? ", median of " + REPEATS + " runs" : "");
        String summary = String.format(Locale.ROOT, "Best: %s on a pool of %d, x%.1f faster than one thread.",
                formatMs(bestMs), bestPool, (double) singleThreadMs / Math.max(bestMs, 1))
                + (repeated ? "  Run order: " + String.join(", ", orders) : "");

        String[] header = {"stage", repeated ? "median" : "time", "min - max", "throughput", "speedup"};
        if (!repeated) {
            // One run per mode: there is no spread to show, so drop that column.
            header = withoutColumn(header, 2);
            rows.replaceAll(row -> withoutColumn(row, 2));
        }
        System.out.println(box(title, header, rows, summary));
    }

    /** One full verifyAll in the given mode: 0 = calling thread only, otherwise a pool of that size. */
    private long measure(int mode, long totalEntries) {
        if (mode == 0) {
            TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
            readOnly.setReadOnly(true);
            AuditChainVerifier sequential = new AuditChainVerifier(signer, publicKeyLoader,
                    applicationRepository, auditLogRepository, readOnly, Runnable::run, BATCH_SIZE, 1);
            warmUp(sequential);
            return checked(sequential.verifyAll(), totalEntries).durationMs();
        }
        AuditChainVerifier verifier = new AuditChainVerifier(signer, publicKeyLoader,
                applicationRepository, auditLogRepository, transactionManager, mode, BATCH_SIZE, DB_READS);
        try {
            warmUp(verifier);
            return checked(verifier.verifyAll(), totalEntries).durationMs();
        } finally {
            verifier.shutdown();
        }
    }

    private static long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    /** Fastest and slowest run, in seconds, to show how noisy the median is. */
    private static String spread(List<Long> values) {
        long min = values.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = values.stream().mapToLong(Long::longValue).max().orElse(0);
        return String.format(Locale.ROOT, "%.2f - %.2f s", min / 1000.0, max / 1000.0);
    }

    /** Draws the report as a table inside a frame, so it stands out in the Maven log. */
    private static String[] withoutColumn(String[] row, int column) {
        List<String> cells = new ArrayList<>(Arrays.asList(row));
        cells.remove(column);
        return cells.toArray(String[]::new);
    }

    private static String box(String title, String[] header, List<String[]> rows, String summary) {
        int[] width = new int[header.length];
        for (int c = 0; c < header.length; c++) {
            width[c] = header[c].length();
            for (String[] row : rows) {
                width[c] = Math.max(width[c], row[c].length());
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(tableRow(header, width));
        int tableWidth = lines.getFirst().length();
        lines.add(null); // separator, drawn below once the frame width is known
        for (String[] row : rows) {
            lines.add(tableRow(row, width));
        }

        int inner = Math.max(tableWidth, Math.max(title.length(), summary.length()));
        String rule = "+" + "=".repeat(inner + 2) + "+";
        StringBuilder out = new StringBuilder("\n").append(rule).append('\n')
                .append(framed(title, inner)).append(rule).append('\n');
        for (String line : lines) {
            out.append(framed(line == null ? "-".repeat(inner) : line, inner));
        }
        return out.append(rule).append('\n').append(framed(summary, inner)).append(rule).toString();
    }

    private static String tableRow(String[] cells, int[] width) {
        // Stage left-aligned, numbers right-aligned so their digits line up.
        StringBuilder row = new StringBuilder(String.format("%-" + width[0] + "s", cells[0]));
        for (int c = 1; c < cells.length; c++) {
            row.append("   ").append(String.format("%" + width[c] + "s", cells[c]));
        }
        return row.toString();
    }

    private static String framed(String text, int inner) {
        return "| " + text + " ".repeat(inner - text.length()) + " |\n";
    }

    private static String perSecondText(long count, long ms) {
        return String.format(Locale.ROOT, "%,.0f entries/s", perSecond(count, ms));
    }

    private static Report checked(Report report, long totalEntries) {
        assertThat(report.signer()).isEqualTo(AuditChainVerifier.Signer.NATIVE);
        assertThat(report.failures()).isEmpty();
        assertThat(report.entriesChecked()).isGreaterThanOrEqualTo(totalEntries);
        return report;
    }

    /** Signs and encrypts every entry with the real modules, then inserts in batches. */
    private long seed() {
        long started = System.nanoTime();
        byte[] orgIndex = new byte[32];
        new SecureRandom().nextBytes(orgIndex);
        jdbc.update("INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) "
                + "VALUES (?, ?, ?, ?, ?)", COMPANY_ID, "perf", orgIndex, "perf", "perf");

        List<Object[]> applications = new ArrayList<>(INSERT_CHUNK);
        List<Object[]> entries = new ArrayList<>(INSERT_CHUNK);
        for (int a = 0; a < APPLICATIONS; a++) {
            long applicationId = FIRST_APPLICATION_ID + a;
            applications.add(new Object[] {applicationId, COMPANY_ID, "1.00", "perf", "UNDER_REVIEW"});
            if (applications.size() == INSERT_CHUNK) {
                insertApplications(applications);
            }

            byte[] previousHash = null;
            for (int seq = 1; seq <= ENTRIES; seq++) {
                String entry = "{\"action\":\"PERF\",\"application\":" + applicationId + ",\"seq\":" + seq + "}";
                SignedEntry signed = signer.signEntry(entry, previousHash);
                entries.add(new Object[] {applicationId, seq, signed.hash(), signed.signature(),
                        piiCodec.encode(entry)});
                previousHash = signed.hash();
                if (entries.size() == INSERT_CHUNK) {
                    insertApplications(applications);
                    insertEntries(entries);
                }
            }
        }
        insertApplications(applications);
        insertEntries(entries);
        return elapsedMs(started);
    }

    private void insertApplications(List<Object[]> rows) {
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO applications (id, company_id, requested_amount, purpose, status) "
                    + "VALUES (?, ?, ?, ?, ?)", rows);
            rows.clear();
        }
    }

    private void insertEntries(List<Object[]> rows) {
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO audit_log (application_id, sequence_number, hash, signature, entry) "
                    + "VALUES (?, ?, ?, ?, ?)", rows);
            rows.clear();
        }
    }

    /** Reads every chain the way the verifier does, decrypting each entry, on one thread. */
    private long loadAndDecryptOnly() {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        long started = System.nanoTime();
        long after = 0;
        long decryptedChars = 0;
        List<Long> ids;
        while (!(ids = applicationRepository.findIdsAfter(after, Limit.of(BATCH_SIZE))).isEmpty()) {
            List<Long> batch = ids;
            Long chars = readOnly.execute(status -> auditLogRepository.findChainsByApplicationIds(batch)
                    .stream().map(AuditLog::getEntry).mapToLong(String::length).sum());
            decryptedChars += chars == null ? 0 : chars;
            after = ids.getLast();
        }
        assertThat(decryptedChars).isPositive();
        return elapsedMs(started);
    }

    /** First native calls and JIT compilation would otherwise land in the first run. */
    private void warmUp(AuditChainVerifier verifier) {
        for (long id = FIRST_APPLICATION_ID; id < FIRST_APPLICATION_ID + Math.min(APPLICATIONS, 200); id++) {
            verifier.verifyApplication(id);
        }
    }

    /**
     * Pool sizes to measure: {@code -Daudit.perf.pools=4,8}, or by default half and all of
     * this machine's processors (half is roughly the physical cores on an SMT CPU).
     */
    private static int[] poolSizes() {
        String configured = System.getProperty("audit.perf.pools", "");
        if (!configured.isBlank()) {
            return Arrays.stream(configured.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
        }
        return IntStream.of(Math.max(1, PROCESSORS / 2), PROCESSORS).distinct().toArray();
    }

    private static String formatMs(long ms) {
        return ms >= 1000 ? String.format(Locale.ROOT, "%d ms (%.2f s)", ms, ms / 1000.0) : ms + " ms";
    }

    private static double perSecond(long count, long ms) {
        return count * 1000.0 / Math.max(ms, 1);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    /** A random 64-byte crypto key and an Ed25519 key pair in a temp dir, never committed. */
    private static Path createKeys() {
        try {
            Path dir = Files.createTempDirectory("resurs-audit-perf-it");
            byte[] cryptoKey = new byte[64];
            new SecureRandom().nextBytes(cryptoKey);
            Files.write(dir.resolve("crypto.key"), cryptoKey);

            KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            writePem(dir.resolve("audit.key"), "PRIVATE KEY", keyPair.getPrivate().getEncoded());
            writePem(dir.resolve("audit.pub"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
            dir.toFile().deleteOnExit();
            for (String name : List.of("crypto.key", "audit.key", "audit.pub")) {
                dir.resolve(name).toFile().deleteOnExit();
            }
            return dir;
        } catch (IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("cannot create test keys", e);
        }
    }

    private static void writePem(Path file, String type, byte[] der) throws IOException {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        Files.writeString(file, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n",
                StandardCharsets.US_ASCII);
    }
}
