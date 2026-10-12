package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import se.comerit.resurs.entity.Company;
import se.comerit.resurs.exception.BankIdSigningException;
import se.comerit.resurs.repository.CompanyRepository;

/**
 * Unit tests for {@link MockBankIdSigningService}, the happy-path BankID
 * signing stand-in.
 *
 * <p>
 * The point of these tests is the one thing the mock refuses. It says yes to
 * everything a registered company with a recorded signatory asks for, so what
 * is worth locking down is that a signature is always distinguishable as a
 * mock's, that the signatory comes from the company record rather than from
 * the caller, and that a company with nobody to sign cannot get a signature
 * at all.
 */
class MockBankIdSigningServiceTest {

    private static final String ORG = "556000-1234";
    private static final String SIGNATORY = "Anders Karlsson";
    private static final String PAYLOAD = "{\"requestedAmount\":500000}";

    private final CompanyRepository companyRepository = mock(CompanyRepository.class);

    private MockBankIdSigningService service() {
        return new MockBankIdSigningService(companyRepository, 0);
    }

    private void companyWith(String signatory) {
        when(companyRepository.findByOrgNumber(ORG))
                .thenReturn(Optional.of(new Company(ORG, "Malmö Fastigheter AB", signatory)));
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Signs and marks the result so it can never pass for a real signature")
        void producesAnObviouslyMockSignature() {
            companyWith(SIGNATORY);

            BankIdSigningService.BankIdSignature signature =
                    service().sign(ORG, PAYLOAD);

            assertThat(signature.signature()).startsWith("MOCK-SIG-");
            assertThat(signature.orderRef()).isNotBlank();
            assertThat(signature.signedAt()).isNotNull();
        }

        @Test
        @DisplayName("Takes the signatory from the company record, not from the caller")
        void signatoryComesFromTheCompanyRecord() {
            companyWith(SIGNATORY);

            // The payload and the org number are all a caller controls; who the
            // record says signed is not among them.
            BankIdSigningService.BankIdSignature signature =
                    service().sign(ORG, PAYLOAD);

            assertThat(signature.signedBy()).isEqualTo(SIGNATORY);
        }

        @Test
        @DisplayName("Two signings produce two distinct orders")
        void eachSigningIsItsOwnOrder() {
            companyWith(SIGNATORY);

            BankIdSigningService.BankIdSignature first = service().sign(ORG, PAYLOAD);
            BankIdSigningService.BankIdSignature second = service().sign(ORG, PAYLOAD);

            assertThat(first.orderRef()).isNotEqualTo(second.orderRef());
            assertThat(first.signature()).isNotEqualTo(second.signature());
        }

        @Test
        @DisplayName("Timestamps the signature at signing time")
        void signatureIsTimestamped() {
            companyWith(SIGNATORY);

            Instant before = Instant.now().minusSeconds(1);

            BankIdSigningService.BankIdSignature signature = service().sign(ORG, PAYLOAD);

            assertThat(signature.signedAt()).isAfterOrEqualTo(before);
            assertThat(signature.signedAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
        }
    }

    @Nested
    @DisplayName("Refusals")
    class Refusals {

        @Test
        @DisplayName("Refuses a company that is not registered")
        void unknownCompanyCannotSign() {
            when(companyRepository.findByOrgNumber(ORG)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().sign(ORG, PAYLOAD))
                    .isInstanceOf(BankIdSigningException.class);
        }

        @Test
        @DisplayName("Refuses a company with no recorded signatory")
        void companyWithoutSignatoryCannotSign() {
            companyWith("   ");

            assertThatThrownBy(() -> service().sign(ORG, PAYLOAD))
                    .isInstanceOf(BankIdSigningException.class);
        }

        @Test
        @DisplayName("Refusal carries no organisation number, so it cannot be used to probe for one")
        void refusalMessageNamesNothing() {
            when(companyRepository.findByOrgNumber(ORG)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().sign(ORG, PAYLOAD))
                    .hasMessageNotContaining(ORG)
                    .hasMessageNotContaining("Malmö");
        }
    }

    @Nested
    @DisplayName("Configured latency")
    class Latency {

        @Test
        @DisplayName("Answers slowly when a delay is configured, like an order waiting on a human")
        void delaysWhenConfigured() {
            companyWith(SIGNATORY);
            MockBankIdSigningService slow =
                    new MockBankIdSigningService(companyRepository, 300);

            Instant start = Instant.now();
            slow.sign(ORG, PAYLOAD);
            long elapsedMs = java.time.Duration.between(start, Instant.now()).toMillis();

            // Lower bound only: a machine under load may overshoot, never undershoot.
            assertThat(elapsedMs).isGreaterThanOrEqualTo(250);
        }

        @Test
        @DisplayName("Answers at once when no delay is configured, as the test suite requires")
        void doesNotDelayByDefault() {
            companyWith(SIGNATORY);

            Instant start = Instant.now();
            service().sign(ORG, PAYLOAD);
            long elapsedMs = java.time.Duration.between(start, Instant.now()).toMillis();

            assertThat(elapsedMs).isLessThan(250);
        }
    }
}
