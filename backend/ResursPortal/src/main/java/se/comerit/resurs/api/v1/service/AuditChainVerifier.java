package se.comerit.resurs.api.v1.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PreDestroy;
import se.comerit.resurs.config.AuditPublicKeyLoader;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.exception.AuditVerificationUnavailableException;
import se.comerit.resurs.exception.CryptoException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;

/**
 * Verifies the audit hash chains stored in {@code audit_log}.
 *
 * <p>Every application has its own short chain, so a full run verifies whole chains
 * application by application, spread over a small pool of its own. The pool is private to
 * this service on purpose: exposing an {@link Executor} bean would make Spring Boot skip its
 * default {@code @Async} pool, which scoring runs on.
 *
 * <p>Rows are read in short read-only transactions and verified after the transaction has
 * closed, so no database connection is held during the Ed25519 work.
 *
 * <p>When a chain fails, each of its entries is re-checked on its own, so the result lists
 * every broken entry with the reason, not only the first one.
 */
@Service
public class AuditChainVerifier {

    private static final Logger log = LoggerFactory.getLogger(AuditChainVerifier.class);

    /** Which signer the chains were verified with; DUMMY results prove nothing. */
    public enum Signer { NATIVE, DUMMY }

    public enum Status { VALID, INVALID, SEQUENCE_GAP, ERROR }

    /** HASH: the entry's content no longer matches its link. SIGNATURE: the link is intact
     * but its signature is not. */
    public enum Reason { HASH, SIGNATURE }

    /** One broken entry of a chain. */
    public record Violation(long sequenceNumber, @NonNull Reason reason) {
    }

    /**
     * The outcome for one application's chain.
     *
     * @param entries          number of entries the chain has
     * @param violations       every broken entry, for {@link Status#INVALID}
     * @param missingSequences sequence numbers absent from 1..n, for
     *                         {@link Status#SEQUENCE_GAP}
     * @param detail           why the chain could not be verified, for {@link Status#ERROR}
     *                         and {@link Status#SEQUENCE_GAP}
     */
    public record ChainResult(long applicationId, @NonNull Status status, int entries,
            @NonNull List<Violation> violations, @NonNull List<Long> missingSequences,
            @Nullable String detail) {

        static ChainResult valid(long applicationId, int entries) {
            return new ChainResult(applicationId, Status.VALID, entries, List.of(), List.of(), null);
        }
    }

    /** The outcome of verifying every chain; only chains that are not valid are listed. */
    public record Report(@NonNull Signer signer, int applicationsChecked, long entriesChecked,
            @NonNull List<ChainResult> failures, long durationMs) {
    }

    /** One stored row, detached from the persistence context. */
    private record ChainRow(long applicationId, long sequenceNumber, byte[] hash, byte[] signature,
            String entry) {
    }

    private final AuditSigningService signer;
    private final AuditPublicKeyLoader publicKeyLoader;
    private final ApplicationRepository applicationRepository;
    private final AuditLogRepository auditLogRepository;
    private final TransactionOperations readOnlyTx;
    private final Executor executor;
    private final @Nullable ExecutorService ownedPool;
    private final int batchSize;
    /** Limits how many batches read from the database at once, so a run leaves
     * connections for request handling; verification itself runs on every pool thread. */
    private final Semaphore dbReads;

    @Autowired
    public AuditChainVerifier(AuditSigningService signer, AuditPublicKeyLoader publicKeyLoader,
            ApplicationRepository applicationRepository, AuditLogRepository auditLogRepository,
            PlatformTransactionManager transactionManager,
            @Value("${resurs.audit.verify.threads:0}") int threads,
            @Value("${resurs.audit.verify.batch-size:500}") int batchSize,
            @Value("${resurs.audit.verify.db-reads:2}") int dbReads) {
        this(signer, publicKeyLoader, applicationRepository, auditLogRepository,
                readOnly(transactionManager), newPool(threads), batchSize, dbReads, true);
    }

    /** For tests: runs on the given executor and transaction, and owns neither. */
    AuditChainVerifier(AuditSigningService signer, AuditPublicKeyLoader publicKeyLoader,
            ApplicationRepository applicationRepository, AuditLogRepository auditLogRepository,
            TransactionOperations readOnlyTx, Executor executor, int batchSize, int dbReads) {
        this(signer, publicKeyLoader, applicationRepository, auditLogRepository,
                readOnlyTx, executor, batchSize, dbReads, false);
    }

    private AuditChainVerifier(AuditSigningService signer, AuditPublicKeyLoader publicKeyLoader,
            ApplicationRepository applicationRepository, AuditLogRepository auditLogRepository,
            TransactionOperations readOnlyTx, Executor executor, int batchSize, int dbReads,
            boolean ownsExecutor) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("resurs.audit.verify.batch-size must be >= 1, got " + batchSize);
        }
        if (dbReads < 1) {
            throw new IllegalArgumentException("resurs.audit.verify.db-reads must be >= 1, got " + dbReads);
        }
        this.signer = signer;
        this.publicKeyLoader = publicKeyLoader;
        this.applicationRepository = applicationRepository;
        this.auditLogRepository = auditLogRepository;
        this.readOnlyTx = readOnlyTx;
        this.executor = executor;
        this.ownedPool = ownsExecutor ? (ExecutorService) executor : null;
        this.batchSize = batchSize;
        this.dbReads = new Semaphore(dbReads, true);
    }

    @PreDestroy
    void shutdown() {
        if (ownedPool != null) {
            ownedPool.shutdown();
        }
    }

    /** Which signer is active; a report produced with {@link Signer#DUMMY} proves nothing. */
    @NonNull
    public Signer signer() {
        return signer instanceof NativeAuditSigningService ? Signer.NATIVE : Signer.DUMMY;
    }

    /**
     * Verifies one application's chain.
     *
     * @throws ApplicationNotFoundException          if there is no such application
     * @throws AuditVerificationUnavailableException if no public key is configured
     */
    @NonNull
    public ChainResult verifyApplication(long applicationId) {
        byte[] publicKey = requirePublicKey();
        List<ChainRow> rows = readOnlyTx.execute(status -> {
            if (!applicationRepository.existsById(applicationId)) {
                throw new ApplicationNotFoundException(applicationId);
            }
            return detach(auditLogRepository.findChainsByApplicationIds(List.of(applicationId)));
        });
        return verifyChain(applicationId, rows, publicKey);
    }

    /**
     * Verifies every application's chain, in parallel batches.
     *
     * <p>Chains appended while the run is in progress are seen in whatever state they were
     * in when their batch was read; entries are only ever added at the end of a chain.
     *
     * @throws AuditVerificationUnavailableException if no public key is configured
     */
    @NonNull
    public Report verifyAll() {
        byte[] publicKey = requirePublicKey();
        long started = System.nanoTime();

        List<CompletableFuture<List<ChainResult>>> batches = new ArrayList<>();
        long after = 0;
        List<Long> ids;
        while (!(ids = applicationRepository.findIdsAfter(after, Limit.of(batchSize))).isEmpty()) {
            List<Long> batch = ids;
            batches.add(CompletableFuture.supplyAsync(() -> verifyBatch(batch, publicKey), executor));
            after = ids.getLast();
        }

        int applications = 0;
        long entries = 0;
        List<ChainResult> failures = new ArrayList<>();
        for (CompletableFuture<List<ChainResult>> batch : batches) {
            for (ChainResult result : batch.join()) {
                applications++;
                entries += result.entries();
                if (result.status() != Status.VALID) {
                    failures.add(result);
                }
            }
        }

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        Report report = new Report(signer(), applications, entries, List.copyOf(failures), durationMs);
        if (failures.isEmpty()) {
            log.info("Audit chain verification: {} applications, {} entries, all valid, signer={}, {} ms",
                    applications, entries, report.signer(), durationMs);
        } else {
            log.warn("Audit chain verification: {} of {} applications failed, signer={}, {} ms",
                    failures.size(), applications, report.signer(), durationMs);
        }
        return report;
    }

    private List<ChainResult> verifyBatch(List<Long> applicationIds, byte[] publicKey) {
        List<ChainRow> rows = readLimited(() -> readOnlyTx.execute(status ->
                detach(auditLogRepository.findChainsByApplicationIds(applicationIds))));

        Map<Long, List<ChainRow>> byApplication = new LinkedHashMap<>();
        for (Long id : applicationIds) {
            byApplication.put(id, new ArrayList<>());
        }
        for (ChainRow row : rows) {
            byApplication.get(row.applicationId()).add(row);
        }

        List<ChainResult> results = new ArrayList<>(applicationIds.size());
        byApplication.forEach((id, chain) -> results.add(verifyChain(id, chain, publicKey)));
        return results;
    }

    private ChainResult verifyChain(long applicationId, List<ChainRow> rows, byte[] publicKey) {
        if (rows.isEmpty()) {
            return ChainResult.valid(applicationId, 0);
        }

        List<Long> missing = missingSequences(rows);
        if (!missing.isEmpty()) {
            return new ChainResult(applicationId, Status.SEQUENCE_GAP, rows.size(), List.of(), missing,
                    "Chain has missing entries; its links and signatures were not checked");
        }

        List<byte[]> hashes = rows.stream().map(ChainRow::hash).toList();
        List<byte[]> signatures = rows.stream().map(ChainRow::signature).toList();
        List<String> entries = rows.stream().map(ChainRow::entry).toList();

        try {
            int firstInvalid = signer.verifyChain(hashes, signatures, entries, publicKey);
            if (firstInvalid == AuditSigningService.VALID_CHAIN) {
                return ChainResult.valid(applicationId, rows.size());
            }

            // Entries before the first broken one already verified; re-check the rest one by one.
            List<Violation> violations = new ArrayList<>();
            for (int i = firstInvalid; i < rows.size(); i++) {
                byte[] previousHash = i == 0 ? null : hashes.get(i - 1);
                int result = signer.verifyChain(List.of(hashes.get(i)), List.of(signatures.get(i)),
                        List.of(entries.get(i)), publicKey, i, previousHash);
                if (result != AuditSigningService.VALID_CHAIN) {
                    Reason reason = linkMatches(previousHash, entries.get(i), hashes.get(i))
                            ? Reason.SIGNATURE
                            : Reason.HASH;
                    violations.add(new Violation(rows.get(i).sequenceNumber(), reason));
                }
            }
            return new ChainResult(applicationId, Status.INVALID, rows.size(), List.copyOf(violations),
                    List.of(), null);
        } catch (CryptoException | IllegalArgumentException e) {
            return new ChainResult(applicationId, Status.ERROR, rows.size(), List.of(), List.of(),
                    e.getMessage());
        }
    }

    /** Sequence numbers are dense from 1; anything absent before the last one is a gap. */
    private static List<Long> missingSequences(List<ChainRow> rows) {
        List<Long> missing = new ArrayList<>();
        long expected = 1;
        for (ChainRow row : rows) {
            while (expected < row.sequenceNumber()) {
                missing.add(expected++);
            }
            expected = row.sequenceNumber() + 1;
        }
        return missing;
    }

    /** Whether SHA-256(previous || entry) still equals the stored link. */
    private static boolean linkMatches(@Nullable byte[] previousHash, String entry, byte[] storedHash) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        sha256.update(previousHash == null ? new byte[AuditSigningService.HASH_LEN] : previousHash);
        sha256.update(entry.getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(sha256.digest(), storedHash);
    }

    private static List<ChainRow> detach(List<AuditLog> logs) {
        return logs.stream()
                .map(log -> new ChainRow(log.getApplication().getId(), log.getSequenceNumber(),
                        log.getHash(), log.getSignature(), log.getEntry()))
                .toList();
    }

    /** Runs a database read once one of the {@code db-reads} permits is free. */
    private <T> T readLimited(Supplier<T> read) {
        dbReads.acquireUninterruptibly();
        try {
            return read.get();
        } finally {
            dbReads.release();
        }
    }

    private byte[] requirePublicKey() {
        return publicKeyLoader.publicKey().orElseThrow(AuditVerificationUnavailableException::new);
    }

    private static TransactionOperations readOnly(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    /** 0 means one thread per available processor: verification is CPU-bound Ed25519 work. */
    private static ExecutorService newPool(int threads) {
        int size = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
        AtomicInteger counter = new AtomicInteger();
        return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(size * 2),
                task -> {
                    Thread thread = new Thread(task, "audit-verify-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
