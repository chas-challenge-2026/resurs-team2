package se.comerit.resurs.api.v1.service;

import org.springframework.stereotype.Service;

import se.comerit.resurs.entity.Application;

@Service
public class EmailService {

    private final EmailProvider emailProvider;

    public EmailService(EmailProvider emailProvider) {
        this.emailProvider = emailProvider;
    }

    public void sendApplicationSubmitted(Application app) {
        emailProvider.send(recipientAddress(app),
                "Ansökan mottagen - #" + app.getId(),
                "Din kreditansökan har mottagits och behandlas.\n\n"
                        + "Ansöknings-ID: " + app.getId() + "\n\n"
                        + "Vi meddelar dig när ett beslut har tagits.");
    }

    public void sendStatusUpdate(Application app) {
        emailProvider.send(recipientAddress(app),
                "Ansökningsstatus uppdaterad - #" + app.getId(),
                "Statusen för din kreditansökan har uppdaterats.\n\n"
                        + "Ansöknings-ID: " + app.getId() + "\n"
                        + "Ny status: " + app.getStatus() + "\n\n"
                        + "Vi granskar din ansökan och uppdaterar dig inom kort.");
    }

    public void sendDecision(Application app) {
        String subject = "Ansökan " + app.getDecision() + " - #" + app.getId();
        String body = "Ett beslut har tagits angående din kreditansökan.\n\n"
                + "Ansöknings-ID: " + app.getId() + "\n"
                + "Beslut: " + app.getDecision() + "\n";
        if (app.getDecisionReason() != null && !app.getDecisionReason().isBlank()) {
            body += "Motivering: " + app.getDecisionReason() + "\n";
        }
        body += "\nTack för din ansökan.";
        emailProvider.send(recipientAddress(app), subject, body);
    }

    private String recipientAddress(Application app) {
        String signatory = app.getCompany().getAuthorizedSignatory();
        if (signatory.contains("@")) {
            return signatory;
        }
        return signatory.trim().toLowerCase().replaceAll("\\s+", "_") + "@example.com";
    }
}
