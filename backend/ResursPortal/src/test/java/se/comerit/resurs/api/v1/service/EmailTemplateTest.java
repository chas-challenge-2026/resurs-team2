package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import se.comerit.resurs.config.EmailTemplateConfig;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Company;
import se.comerit.resurs.entity.Decision;

/**
 * Verifies that transactional email is rendered from the Thymeleaf text
 * templates under {@code email-templates/} (subject = first line, body after
 * the {@code ---} delimiter) rather than hardcoded strings.
 */
class EmailTemplateTest {

    private EmailProvider emailProvider;
    private EmailService emailService;
    private Application app;

    @BeforeEach
    void setUp() {
        emailProvider = mock(EmailProvider.class);
        emailService = new EmailService(emailProvider, new EmailTemplateConfig().emailTemplateEngine(false));
        app = new Application(new Company("556677-8899", "Testbolaget AB", "Kalle Kula"),
                new BigDecimal("300000"), "Rörelsekapital");
        setId(app, 7L);
    }

    @Test
    @DisplayName("Application-received email renders subject and body from the template")
    void applicationSubmittedEmail() {
        emailService.sendApplicationSubmitted(app);

        String[] email = captureEmail();
        assertThat(email[0]).isEqualTo("kalle_kula@example.com");
        assertThat(email[1]).isEqualTo("Ansökan mottagen - #7");
        assertThat(email[2])
                .contains("Hej Kalle Kula")
                .contains("Ansöknings-ID: 7")
                .contains("Företag: Testbolaget AB")
                .contains("Organisationsnummer: 556677-8899")
                .contains("Önskat belopp: 300000 kronor")
                .contains("Ändamål: Rörelsekapital")
                .contains("Vi meddelar dig när ett beslut har tagits.");
    }

    @Test
    @DisplayName("Status-update email renders the new status from the template")
    void statusUpdateEmail() {
        app.setStatus(ApplicationStatus.UNDER_REVIEW);

        emailService.sendStatusUpdate(app);

        String[] email = captureEmail();
        assertThat(email[1]).isEqualTo("Ansökningsstatus uppdaterad - #7");
        assertThat(email[2])
                .contains("Ansöknings-ID: 7")
                .contains("Företag: Testbolaget AB")
                .contains("Ny status: UNDER_REVIEW");
    }

    @Test
    @DisplayName("Decision email includes the reason when one is present")
    void decisionEmailWithReason() {
        app.setStatus(ApplicationStatus.APPROVED);
        app.setDecision(Decision.APPROVED);
        app.setDecisionReason("Bra ekonomi");

        emailService.sendDecision(app);

        String[] email = captureEmail();
        assertThat(email[1]).isEqualTo("Ansökan APPROVED - #7");
        assertThat(email[2])
                .contains("Beslut: APPROVED")
                .contains("Motivering: Bra ekonomi")
                .contains("Tack för din ansökan.")
                .doesNotContain("\\n");
    }

    @Test
    @DisplayName("Decision email never prints a literal backslash-n after the reason")
    void decisionEmailDoesNotPrintLiteralNewline() {
        app.setStatus(ApplicationStatus.REJECTED);
        app.setDecision(Decision.REJECTED);
        app.setDecisionReason("Kreditbelopp överstiger årsoms. (50000 kr > 0 kr). ");

        emailService.sendDecision(app);

        String[] email = captureEmail();
        assertThat(email[2])
                .contains("Motivering: Kreditbelopp överstiger årsoms. (50000 kr > 0 kr).")
                .contains("Tack för din ansökan.")
                .doesNotContain("\\n")
                .doesNotContain("&gt;");
    }

    @Test
    @DisplayName("Decision email omits the reason line when none is present")
    void decisionEmailWithoutReason() {
        app.setStatus(ApplicationStatus.REJECTED);
        app.setDecision(Decision.REJECTED);
        app.setDecisionReason(null);

        emailService.sendDecision(app);

        String[] email = captureEmail();
        assertThat(email[1]).isEqualTo("Ansökan REJECTED - #7");
        assertThat(email[2])
                .contains("Beslut: REJECTED")
                .doesNotContain("Motivering")
                .contains("Tack för din ansökan.");
    }

    private String[] captureEmail() {
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailProvider).send(to.capture(), subject.capture(), body.capture());
        return new String[] { to.getValue(), subject.getValue(), body.getValue() };
    }

    private void setId(Application application, long id) {
        try {
            var field = Application.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(application, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}