package se.comerit.resurs.api.v1.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import se.comerit.resurs.entity.Application;
import se.comerit.resurs.rating.ApplicationData;

import tools.jackson.databind.ObjectMapper;

/**
 * Composes and sends transactional email. Copy lives in Thymeleaf text
 * templates under {@code email-templates/}: the first line of each file is the
 * subject, a {@code ---} delimiter separates it from the body.
 *
 * <p>
 * Templates receive the whole {@link Application} as {@code app} and the
 * parsed {@code financial_data} JSON as {@code financial} (an
 * {@link ApplicationData}, or null when absent), so any application or
 * financial figure can be interpolated.
 */
@Service
public class EmailService {

    private record EmailMessage(String subject, String body) {
    }

    private final EmailProvider emailProvider;
    private final TemplateEngine emailTemplateEngine;
    private final ObjectMapper objectMapper;

    public EmailService(EmailProvider emailProvider,
            @Qualifier("emailTemplateEngine") TemplateEngine emailTemplateEngine,
            ObjectMapper objectMapper) {
        this.emailProvider = emailProvider;
        this.emailTemplateEngine = emailTemplateEngine;
        this.objectMapper = objectMapper;
    }

    public void sendApplicationSubmitted(Application app) {
        sendFromTemplate("application-submitted", app);
    }

    public void sendStatusUpdate(Application app) {
        sendFromTemplate("status-updated", app);
    }

    public void sendDecision(Application app) {
        sendFromTemplate("decision", app);
    }

    private void sendFromTemplate(String template, Application app) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("app", app);
        model.put("financial", parseFinancialData(app));
        EmailMessage message = render(template, model);
        emailProvider.send(recipientAddress(app), message.subject(), message.body());
    }

    private ApplicationData parseFinancialData(Application app) {
        String json = app.getFinancialData();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ApplicationData.class);
        } catch (Exception _) {
            return null;
        }
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