package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import se.comerit.resurs.api.v1.service.AuditChainVerifier.ChainResult;
import se.comerit.resurs.api.v1.service.AuditChainVerifier.Reason;
import se.comerit.resurs.api.v1.service.AuditChainVerifier.Report;
import se.comerit.resurs.api.v1.service.AuditChainVerifier.Signer;
import se.comerit.resurs.api.v1.service.AuditChainVerifier.Status;
import se.comerit.resurs.api.v1.service.AuditChainVerifier.Violation;
import se.comerit.resurs.api.v1.service.AuditSigningService.SignedEntry;
import se.comerit.resurs.config.AuditPublicKeyLoader;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.exception.AuditVerificationUnavailableException;
import se.comerit.resurs.exception.CryptoException;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;

/**
 * Chains are built and checked with the real {@link DummyAuditSigningService}, which links
 * entries exactly as the native module does, so edited content and edited signatures are
 * detected for real. Only the repositories and the key loader are mocked, and everything
 * runs on the calling thread.
 */
class AuditChainVerifierTest {

    private static final byte[] KEY = new byte[AuditSigningService.PUBLIC_KEY_LEN];

    private final DummyAuditSigningService dummy = new DummyAuditSigningService();
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final AuditLogRepository auditLogs = mock(AuditLogRepository.class);
    private final AuditPublicKeyLoader keyLoader = mock(AuditPublicKeyLoader.class);

    @BeforeEach
    void setUp() {
        when(keyLoader.publicKey()).thenReturn(Optional.of(KEY));
    }

    @Test
    void anIntactChainIsValid() {
        givenApplication(10, chain(10, 3));

        ChainResult result = verifier(dummy, 10).verifyApplication(10);

        assertThat(result.status()).isEqualTo(Status.VALID);
        assertThat(result.entries()).isEqualTo(3);
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void editedContentIsReportedAsHashAtThatEntry() {
        List<AuditLog> rows = chain(10, 3);
        rows.set(1, withEntry(rows.get(1), "{\"app\":10,\"seq\":2,\"edited\":true}"));
        givenApplication(10, rows);

        ChainResult result = verifier(dummy, 10).verifyApplication(10);

        assertThat(result.status()).isEqualTo(Status.INVALID);
        assertThat(result.violations()).containsExactly(new Violation(2, Reason.HASH));
    }

    @Test
    void editedSignatureIsReportedAsSignature() {
        List<AuditLog> rows = chain(10, 3);
        rows.set(0, withSignature(rows.get(0), flipLastBit(rows.get(0).getSignature())));
        givenApplication(10, rows);

        ChainResult result = verifier(dummy, 10).verifyApplication(10);

        assertThat(result.violations()).containsExactly(new Violation(1, Reason.SIGNATURE));
    }

    @Test
    void everyBrokenEntryIsListedNotOnlyTheFirst() {
        List<AuditLog> rows = chain(10, 4);
        rows.set(0, withEntry(rows.get(0), "{\"app\":10,\"seq\":1,\"edited\":true}"));
        rows.set(2, withSignature(rows.get(2), flipLastBit(rows.get(2).getSignature())));
        givenApplication(10, rows);

        ChainResult result = verifier(dummy, 10).verifyApplication(10);

        assertThat(result.status()).isEqualTo(Status.INVALID);
        assertThat(result.violations()).containsExactly(
                new Violation(1, Reason.HASH),
                new Violation(3, Reason.SIGNATURE));
    }

    @Test
    void aMissingEntryIsASequenceGap() {
        List<AuditLog> rows = chain(10, 4);
        rows.remove(1);
        givenApplication(10, rows);

        ChainResult result = verifier(dummy, 10).verifyApplication(10);

        assertThat(result.status()).isEqualTo(Status.SEQUENCE_GAP);
        assertThat(result.missingSequences()).containsExactly(2L);
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void aMissingFirstEntryIsASequenceGap() {
        List<AuditLog> rows = chain(10, 3);
        rows.remove(0);
        givenApplication(10, rows);

        ChainResult result = verifier(dummy, 10).verifyApplication(10);

        assertThat(result.status()).isEqualTo(Status.SEQUENCE_GAP);
        assertThat(result.missingSequences()).containsExactly(1L);
    }

    @Test
    void anApplicationWithoutEntriesIsValid() {
        givenApplication(10, List.of());

        ChainResult result = verifier(dummy, 10).verifyApplication(10);

        assertThat(result.status()).isEqualTo(Status.VALID);
        assertThat(result.entries()).isZero();
    }

    @Test
    void verifyAllCoversEveryApplicationAcrossBatches() {
        List<AuditLog> chain20 = chain(20, 2);
        chain20.set(1, withEntry(chain20.get(1), "{\"app\":20,\"seq\":2,\"edited\":true}"));
        List<AuditLog> batch1 = new ArrayList<>(chain(10, 3));
        batch1.addAll(chain20);
        when(applications.findIdsAfter(eq(0L), any())).thenReturn(List.of(10L, 20L));
        when(applications.findIdsAfter(eq(20L), any())).thenReturn(List.of(30L));
        when(applications.findIdsAfter(eq(30L), any())).thenReturn(List.of());
        when(auditLogs.findChainsByApplicationIds(List.of(10L, 20L))).thenReturn(batch1);
        when(auditLogs.findChainsByApplicationIds(List.of(30L))).thenReturn(List.of());

        Report report = verifier(dummy, 2).verifyAll();

        assertThat(report.signer()).isEqualTo(Signer.DUMMY);
        assertThat(report.applicationsChecked()).isEqualTo(3);
        assertThat(report.entriesChecked()).isEqualTo(5);
        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.applicationId()).isEqualTo(20);
            assertThat(failure.status()).isEqualTo(Status.INVALID);
            assertThat(failure.violations()).containsExactly(new Violation(2, Reason.HASH));
        });
    }

    @Test
    void aNativeFailureIsAnErrorForThatApplicationOnly() {
        List<AuditLog> rows = new ArrayList<>(chain(10, 2));
        rows.addAll(chain(20, 2));
        when(applications.findIdsAfter(eq(0L), any())).thenReturn(List.of(10L, 20L));
        when(applications.findIdsAfter(eq(20L), any())).thenReturn(List.of());
        when(auditLogs.findChainsByApplicationIds(List.of(10L, 20L))).thenReturn(rows);

        Report report = verifier(failingFor(10), 10).verifyAll();

        assertThat(report.applicationsChecked()).isEqualTo(2);
        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.applicationId()).isEqualTo(10);
            assertThat(failure.status()).isEqualTo(Status.ERROR);
            assertThat(failure.detail()).contains("Native crypto error");
        });
    }

    @Test
    void anEmptyDatabaseGivesAnEmptyReport() {
        when(applications.findIdsAfter(eq(0L), any())).thenReturn(List.of());

        Report report = verifier(dummy, 10).verifyAll();

        assertThat(report.applicationsChecked()).isZero();
        assertThat(report.entriesChecked()).isZero();
        assertThat(report.failures()).isEmpty();
    }

    @Test
    void withoutAPublicKeyVerificationIsUnavailable() {
        when(keyLoader.publicKey()).thenReturn(Optional.empty());
        AuditChainVerifier verifier = verifier(dummy, 10);

        assertThatThrownBy(() -> verifier.verifyApplication(10))
                .isInstanceOf(AuditVerificationUnavailableException.class);
        assertThatThrownBy(verifier::verifyAll)
                .isInstanceOf(AuditVerificationUnavailableException.class);
    }

    @Test
    void anUnknownApplicationIsNotFound() {
        when(applications.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> verifier(dummy, 10).verifyApplication(99))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void databaseReadsNeverExceedTheConfiguredLimit() throws Exception {
        int batches = 12;
        int dbReads = 2;
        when(applications.findIdsAfter(anyLong(), any())).thenAnswer(call -> {
            long after = call.getArgument(0);
            return after < batches ? List.of(after + 1) : List.of();
        });
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        when(auditLogs.findChainsByApplicationIds(any())).thenAnswer(call -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            Thread.sleep(20); // hold the "connection" long enough for reads to overlap
            inFlight.decrementAndGet();
            return List.of();
        });

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            AuditChainVerifier verifier = new AuditChainVerifier(dummy, keyLoader, applications, auditLogs,
                    TransactionOperations.withoutTransaction(), pool, 1, dbReads);

            Report report = verifier.verifyAll();

            assertThat(report.applicationsChecked()).isEqualTo(batches);
            assertThat(maxInFlight.get()).isBetween(1, dbReads);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aDbReadLimitBelowOneIsRejected() {
        assertThatThrownBy(() -> new AuditChainVerifier(dummy, keyLoader, applications, auditLogs,
                TransactionOperations.withoutTransaction(), Runnable::run, 10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("db-reads");
    }

    @Test
    void theNativeSignerIsLabelledNative() {
        assertThat(verifier(mock(NativeAuditSigningService.class), 10).signer()).isEqualTo(Signer.NATIVE);
        assertThat(verifier(dummy, 10).signer()).isEqualTo(Signer.DUMMY);
    }

    private AuditChainVerifier verifier(AuditSigningService signer, int batchSize) {
        return new AuditChainVerifier(signer, keyLoader, applications, auditLogs,
                TransactionOperations.withoutTransaction(), Runnable::run, batchSize, 1);
    }

    private void givenApplication(long id, List<AuditLog> rows) {
        when(applications.existsById(id)).thenReturn(true);
        when(auditLogs.findChainsByApplicationIds(List.of(id))).thenReturn(rows);
    }

    /** A correctly signed chain of {@code length} entries, numbered from 1. */
    private List<AuditLog> chain(long applicationId, int length) {
        Application application = mock(Application.class);
        when(application.getId()).thenReturn(applicationId);
        List<AuditLog> rows = new ArrayList<>();
        byte[] previousHash = null;
        for (int seq = 1; seq <= length; seq++) {
            String entry = "{\"app\":" + applicationId + ",\"seq\":" + seq + "}";
            SignedEntry signed = dummy.signEntry(entry, previousHash);
            rows.add(new AuditLog(application, seq, signed.hash(), signed.signature(), entry));
            previousHash = signed.hash();
        }
        return rows;
    }

    private static AuditLog withEntry(AuditLog row, String entry) {
        return new AuditLog(row.getApplication(), row.getSequenceNumber(), row.getHash(), row.getSignature(),
                entry);
    }

    private static AuditLog withSignature(AuditLog row, byte[] signature) {
        return new AuditLog(row.getApplication(), row.getSequenceNumber(), row.getHash(), signature,
                row.getEntry());
    }

    private static byte[] flipLastBit(byte[] value) {
        byte[] copy = value.clone();
        copy[copy.length - 1] ^= 0x01;
        return copy;
    }

    /** The dummy signer, except that verifying any entry of {@code applicationId} fails natively. */
    private AuditSigningService failingFor(long applicationId) {
        String marker = "\"app\":" + applicationId + ",";
        return new AuditSigningService() {
            @Override
            public @NonNull SignedEntry signEntry(@NonNull String entryJson, @Nullable byte[] previousHash) {
                return dummy.signEntry(entryJson, previousHash);
            }

            @Override
            public int verifyChain(@NonNull List<byte[]> hashes, @NonNull List<byte[]> signatures,
                    @NonNull List<String> entries, @NonNull byte[] publicKey,
                    long startIndex, @Nullable byte[] previousHash) {
                if (entries.stream().anyMatch(entry -> entry.contains(marker))) {
                    throw new CryptoException(-5);
                }
                return dummy.verifyChain(hashes, signatures, entries, publicKey, startIndex, previousHash);
            }
        };
    }
}
