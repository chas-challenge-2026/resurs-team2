package se.comerit.resurs.api.v1.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import se.comerit.resurs.entity.Application;

/**
 * Composes and sends transactional email. Copy lives in Thymeleaf text
 * templates under {@code email-templates/}: the first line of each file is the
 * subject, a {@code ---} delimiter separates it from the body.
 */
@Service
public class EmailService {

    private record EmailMessage(String subject, String body) {
    }

    private final EmailProvider emailProvider;
    private final TemplateEngine emailTemplateEngine;

    public EmailService(EmailProvider emailProvider,
            @Qualifier("emailTemplateEngine") TemplateEngine emailTemplateEngine) {
        this.emailProvider = emailProvider;
        this.emailTemplateEngine = emailTemplateEngine;
    }

    public void sendApplicationSubmitted(Application app) {
        EmailMessage message = render("application-submitted", Map.of("app", app));
        emailProvider.send(recipientAddress(app), message.subject(), message.body());
    }

    public void sendStatusUpdate(Application app) {
        EmailMessage message = render("status-updated", Map.of("app", app));
        emailProvider.send(recipientAddress(app), message.subject(), message.body());
    }

    public void sendDecision(Application app) {
        EmailMessage message = render("decision", Map.of("app", app));
        emailProvider.send(recipientAddress(app), message.subject(), message.body());
    }

    private EmailMessage render(String template, Map<String, Object> model) {
        Context context = new Context();
        context.setVariables(model);
        String rendered = emailTemplateEngine.process(template, context);

        String[] parts = rendered.split("\\R---\\R", 2);
        return new EmailMessage(parts[0].trim(), parts.length > 1 ? parts[1].trim() : "");
    }

    private String recipientAddress(Application app) {
        String signatory = app.getCompany().getAuthorizedSignatory();
        if (signatory.contains("@")) {
            return signatory;
        }
        return signatory.trim().toLowerCase().replaceAll("\\s+", "_") + "@example.com";
    }
}