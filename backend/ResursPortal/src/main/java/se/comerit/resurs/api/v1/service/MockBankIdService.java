package se.comerit.resurs.api.v1.service;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Happy-path stand-in for BankID authentication: an organisation number is
 * accepted if it is one of the seeded companies.
 *
 * <p>
 * Selected by {@code resurs.bankid.mode=mock}, the same property that selects
 * {@link MockBankIdSigningService}, so authentication and signing can never end
 * up on different implementations. A real implementation is added by declaring a
 * bean with {@code resurs.bankid.mode=real}; with no such bean the application
 * fails to start rather than quietly running the mock.
 */
@Service
@ConditionalOnProperty(name = "resurs.bankid.mode", havingValue = "mock", matchIfMissing = true)
public class MockBankIdService implements BankIdService {

    private static final Set<String> WHITELIST = Set.of("556000-1234", "556000-5678");

    @Override
    public boolean authenticate(String orgNumber) {
        return WHITELIST.contains(orgNumber);
    }

}
