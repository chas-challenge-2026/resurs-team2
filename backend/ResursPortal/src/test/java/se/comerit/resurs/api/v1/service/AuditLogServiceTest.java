package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import se.comerit.resurs.audit.ApplicationCreated;
import se.comerit.resurs.audit.EtaSet;
import se.comerit.resurs.audit.ManualDecision;
import se.comerit.resurs.audit.ScoringRun;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.AuditLog;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.repository.ApplicationRepository;
import se.comerit.resurs.repository.AuditLogRepository;
import tools.jackson.databind.ObjectMapper;

class AuditLogServiceTest {

    private AuditLogRepository auditLogRepository;
    private ApplicationRepository applicationRepository;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        auditLogService = new AuditLogService(auditLogRepository, applicationRepository,
                new ObjectMapper());
    }

    private Application application() {
        Company company = new Company("556677-8899", "Testbolaget AB", "Kalle Kula");
        return new Application(company, new BigDecimal("500000"), "Rörelsekapital");
    }

    private List<AuditLog> savedLogs() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    @DisplayName("Serialization of audit entries")
    class Serialization {

        @Test
        @DisplayName("APPLICATION_CREATED entry carries the legacy action and org number")
        void applicationCreatedSerialization() {
            auditLogService.append(application(), new ApplicationCreated("556677-8899"));

            AuditLog log = savedLogs().get(0);
            assertThat(log.getEntry())
                    .isEqualTo("{\"action\":\"APPLICATION_CREATED\",\"orgNumber\":\"556677-8899\"}");
        }

        @Test
        @DisplayName("SCORING_RUN entry carries the legacy action, result and flag count")
        void scoringRunSerialization() {
            auditLogService.append(application(), new ScoringRun("APPROVED", "0"));

            AuditLog log = savedLogs().get(0);
            assertThat(log.getEntry())
                    .contains("\"action\":\"SCORING_RUN\"")
                    .contains("\"result\":\"APPROVED\"")
                    .contains("\"flags\":\"0\"");
        }

        @Test
        @DisplayName("MANUAL_DECISION entry omits a null comment")
        void manualDecisionOmitsNullComment() {
            auditLogService.append(application(), new ManualDecision("APPROVED", "Anna Andersson", null));

            AuditLog log = savedLogs().get(0);
            assertThat(log.getEntry())
                    .isEqualTo("{\"action\":\"MANUAL_DECISION\",\"decision\":\"APPROVED\",\"worker\":\"Anna Andersson\"}");
        }

        @Test
        @DisplayName("MANUAL_DECISION entry includes a comment when present")
        void manualDecisionIncludesComment() {
            auditLogService.append(application(), new ManualDecision("REJECTED", "Anna Andersson", "Saknar omsättning"));

            AuditLog log = savedLogs().get(0);
            assertThat(log.getEntry())
                    .contains("\"action\":\"MANUAL_DECISION\"")
                    .contains("\"decision\":\"REJECTED\"")
                    .contains("\"worker\":\"Anna Andersson\"")
                    .contains("\"comment\":\"Saknar omsättning\"");
        }

        @Test
        @DisplayName("ETA_SET entry carries the ISO-8601 estimated resolution time")
        void etaSetSerialization() {
            auditLogService.append(application(), new EtaSet("2026-09-28T10:00:00Z"));

            AuditLog log = savedLogs().get(0);
            assertThat(log.getEntry())
                    .isEqualTo("{\"action\":\"ETA_SET\",\"estimatedResolutionAt\":\"2026-09-28T10:00:00Z\"}");
        }

        @Test
        @DisplayName("ETA_SET entry omits the estimated resolution time when cleared")
        void etaSetOmitsNullEstimatedTime() {
            auditLogService.append(application(), new EtaSet(null));

            AuditLog log = savedLogs().get(0);
            assertThat(log.getEntry()).isEqualTo("{\"action\":\"ETA_SET\"}");
        }
    }

    @Nested
    @DisplayName("Chain metadata")
    class Chain {

        @Test
        @DisplayName("First entry uses sequence 1")
        void firstEntryStartsChain() {
            when(auditLogRepository.getNextSequenceNumber(any(Application.class))).thenReturn(0L);

            auditLogService.append(application(), new ApplicationCreated("556677-8899"));

            assertThat(savedLogs().get(0).getSequenceNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("Subsequent entry continues the sequence")
        void subsequentEntryContinuesSequence() {
            when(auditLogRepository.getNextSequenceNumber(any(Application.class))).thenReturn(1L);

            auditLogService.append(application(), new ManualDecision("APPROVED", "Anna Andersson", null));

            assertThat(savedLogs().get(0).getSequenceNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("Locks the application before reading the chain, then writes once")
        void locksApplicationBeforeReadingChain() {
            when(auditLogRepository.getNextSequenceNumber(any(Application.class))).thenReturn(0L);

            auditLogService.append(application(), new ApplicationCreated("556677-8899"));

            // The lock is what removes the read-then-insert race: it must be taken before
            // the sequence number and predecessor hash are read, not after, or another
            // append could commit in between and leave this entry chaining from a hash
            // that is no longer its predecessor.
            InOrder inOrder = inOrder(applicationRepository, auditLogRepository);
            inOrder.verify(applicationRepository).lockById(any());
            inOrder.verify(auditLogRepository).getNextSequenceNumber(any(Application.class));
            inOrder.verify(auditLogRepository).findPreviousHash(any(Application.class));
            inOrder.verify(auditLogRepository).save(any(AuditLog.class));

            // Exactly one insert: no retry loop, because the lock makes a collision
            // impossible rather than recoverable.
            verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        }
    }
}