package se.comerit.resurs.api.v1.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import se.comerit.resurs.exception.BankIdSigningException;
import se.comerit.resurs.repository.CompanyRepository;

/**
 * Happy-path stand-in for BankID signing: it signs, and it always says yes.
 *
 * <p>
 * The one thing it refuses is an application with nobody behind it. Signing on
 * behalf of a company that has no recorded authorised signatory would produce
 * evidence of a signature no one gave, so that case fails rather than
 * succeeding silently -- which is also the signatory-authority check
 * {@code docs/v2-targets.md} asks the mock flow to perform. Every seeded company
 * has a signatory, so this never fires in practice.
 *
 * <p>
 * Answering <em>slowly</em> is deliberate for the same reason
 * {@link MockCompanyRegistryClient} answers slowly: a step that returns
 * instantly hides the fact that a real order waits on a human opening an app.
 * The delay is configurable and is zero in tests.
 *
 * <p>
 * Selected by {@code resurs.bankid.mode=mock}, which is also the default. A real
 * implementation is added by declaring a bean with
 * {@code resurs.bankid.mode=real} -- nothing here, and nothing that calls the
 * interface, changes.
 */
@Service
@ConditionalOnProperty(name = "resurs.bankid.mode", havingValue = "mock", matchIfMissing = true)
public class MockBankIdSigningService implements BankIdSigningService {

    private static final Logger log = LoggerFactory.getLogger(MockBankIdSigningService.class);

    /** Marks the signature as a mock's, so it can never pass for a real one. */
    private static final String SIGNATURE_PREFIX = "MOCK-SIG-";

    private final CompanyRepository companyRepository;
    private final Duration latency;

    public MockBankIdSigningService(
            CompanyRepository companyRepository,
            @Value("${resurs.bankid.mock-latency-ms:0}") long mockLatencyMs) {
        this.companyRepository = companyRepository;
        this.latency = Duration.ofMillis(Math.max(0, mockLatencyMs));
    }

    @Override
    public BankIdSignature sign(String orgNumber, String payloadJson) {

        // The signatory is the company's recorded one, not a name the client sent.
        // Anything else would let the caller choose who the record says signed.
        String signatory = companyRepository.findByOrgNumber(orgNumber)
                .map(company -> company.getAuthorizedSignatory())
                .filter(name -> name != null && !name.isBlank())
                .orElseThrow(BankIdSigningException::signingFailed);

        if (!latency.isZero()) {
            try {
                Thread.sleep(latency.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw BankIdSigningException.signingFailed();
            }
        }

        String orderRef = UUID.randomUUID().toString();
        BankIdSignature signature = new BankIdSignature(
                orderRef,
                SIGNATURE_PREFIX + orderRef.replace("-", ""),
                signatory,
                Instant.now());

        log.debug("Mock BankID signing completed for order {}", orderRef);
        return signature;
    }
}
