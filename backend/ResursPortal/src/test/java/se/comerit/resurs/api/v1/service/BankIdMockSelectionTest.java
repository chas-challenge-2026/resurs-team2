package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import se.comerit.resurs.repository.CompanyRepository;

/**
 * Locks down how a BankID implementation is selected, because that selection is
 * the whole point of the seam: authentication and signing are two halves of one
 * integration, and they are chosen together or not at all.
 *
 * <p>
 * The second test is the one that matters. A real implementation is meant to be
 * added by flipping {@code resurs.bankid.mode} and declaring a bean; if that
 * flip silently left a mock behind, the application would appear to be running
 * against BankID while still answering with the whitelist. Refusing to start is
 * the only safe outcome, so what is asserted here is that the mocks are gone
 * rather than that anything in particular replaces them.
 */
class BankIdMockSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MockBankIdService.class, MockBankIdSigningService.class)
            .withBean(CompanyRepository.class, () -> mock(CompanyRepository.class));

    @Test
    @DisplayName("The mock is the default, so both halves run on it out of the box")
    void mockIsSelectedByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(BankIdService.class);
            assertThat(context).hasSingleBean(BankIdSigningService.class);

            assertThat(context.getBean(BankIdService.class))
                    .isInstanceOf(MockBankIdService.class);
            assertThat(context.getBean(BankIdSigningService.class))
                    .isInstanceOf(MockBankIdSigningService.class);
        });
    }

    @Test
    @DisplayName("Selecting a real implementation drops both mocks, so the halves cannot drift apart")
    void realModeDropsBothMocks() {
        runner.withPropertyValues("resurs.bankid.mode=real").run(context -> {
            assertThat(context).doesNotHaveBean(BankIdService.class);
            assertThat(context).doesNotHaveBean(BankIdSigningService.class);
        });
    }
}
