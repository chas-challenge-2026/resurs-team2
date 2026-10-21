package se.comerit.resurs.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import se.comerit.resurs.api.v1.service.DummyCryptoService;
import se.comerit.resurs.config.PiiIndexProvider;
import se.comerit.resurs.config.PlainPiiCodec;
import se.comerit.resurs.entity.AmountAttributeConverter;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.entity.CompanyBlindIndexListener;
import se.comerit.resurs.entity.PiiAttributeConverter;

/**
 * The two read queries the audit chain verifier depends on: paging through application ids,
 * and loading the chains of a batch of applications in chain order.
 *
 * <p>Application ids have gaps on purpose, and audit rows are inserted out of order, so the
 * tests catch paging that assumes dense ids and loading that relies on insertion order.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({PiiAttributeConverter.class, AmountAttributeConverter.class, PlainPiiCodec.class,
        CompanyBlindIndexListener.class, DummyCryptoService.class, PiiIndexProvider.class})
@Sql(statements = {
        "DELETE FROM audit_log",
        "DELETE FROM documents",
        "DELETE FROM applications",
        "DELETE FROM companies",
        "INSERT INTO companies (id, org_number, org_number_index, company_name, authorized_signatory) VALUES (1, '556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Anders Karlsson')",
        "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (10, 1, '100000.00', 'A', 'UNDER_REVIEW')",
        "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (20, 1, '200000.00', 'B', 'UNDER_REVIEW')",
        "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (30, 1, '300000.00', 'C', 'UNDER_REVIEW')",
        "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (40, 1, '400000.00', 'D', 'UNDER_REVIEW')",
        "INSERT INTO applications (id, company_id, requested_amount, purpose, status) VALUES (50, 1, '500000.00', 'E', 'UNDER_REVIEW')",
        "INSERT INTO audit_log (application_id, sequence_number, hash, signature, entry) VALUES (20, 3, X'23', X'53', 'app20-seq3')",
        "INSERT INTO audit_log (application_id, sequence_number, hash, signature, entry) VALUES (10, 2, X'12', X'52', 'app10-seq2')",
        "INSERT INTO audit_log (application_id, sequence_number, hash, signature, entry) VALUES (40, 1, X'41', X'51', 'app40-seq1')",
        "INSERT INTO audit_log (application_id, sequence_number, hash, signature, entry) VALUES (20, 1, X'21', X'51', 'app20-seq1')",
        "INSERT INTO audit_log (application_id, sequence_number, hash, signature, entry) VALUES (10, 1, X'11', X'51', 'app10-seq1')",
        "INSERT INTO audit_log (application_id, sequence_number, hash, signature, entry) VALUES (20, 2, X'22', X'52', 'app20-seq2')"
})
class AuditVerificationQueriesTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void pagingByLastIdVisitsEveryApplicationOnce() {
        List<Long> seen = new ArrayList<>();
        List<Integer> pageSizes = new ArrayList<>();
        long after = 0;

        List<Long> page;
        while (!(page = applicationRepository.findIdsAfter(after, Limit.of(2))).isEmpty()) {
            pageSizes.add(page.size());
            seen.addAll(page);
            after = page.getLast();
        }

        assertThat(seen).containsExactly(10L, 20L, 30L, 40L, 50L);
        assertThat(pageSizes).containsExactly(2, 2, 1);
    }

    @Test
    void pagingPastTheLastIdReturnsNothing() {
        assertThat(applicationRepository.findIdsAfter(50, Limit.of(10))).isEmpty();
    }

    @Test
    void chainsAreGroupedByApplicationAndInSequenceOrder() {
        List<AuditLog> rows = auditLogRepository.findChainsByApplicationIds(List.of(20L, 10L));

        assertThat(rows).extracting(AuditLog::getEntry).containsExactly(
                "app10-seq1", "app10-seq2",
                "app20-seq1", "app20-seq2", "app20-seq3");
        assertThat(rows).extracting(row -> row.getApplication().getId())
                .containsExactly(10L, 10L, 20L, 20L, 20L);
        assertThat(rows).extracting(AuditLog::getSequenceNumber)
                .containsExactly(1L, 2L, 1L, 2L, 3L);
    }

    @Test
    void chainsOfOtherApplicationsAreLeftOut() {
        List<AuditLog> rows = auditLogRepository.findChainsByApplicationIds(List.of(40L));

        assertThat(rows).extracting(AuditLog::getEntry).containsExactly("app40-seq1");
    }

    @Test
    void applicationWithoutEntriesHasEmptyChain() {
        assertThat(auditLogRepository.findChainsByApplicationIds(List.of(30L))).isEmpty();
    }
}
