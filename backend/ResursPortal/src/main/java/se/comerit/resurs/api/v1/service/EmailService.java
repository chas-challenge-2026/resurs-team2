package se.comerit.resurs.api.v1.service;

import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final EmailProvider emailProvider;

    public EmailService(EmailProvider emailProvider) {
        this.emailProvider = emailProvider;
    }

    public void sendApplicationSubmitted(String to, Long applicationId) {
        emailProvider.send(to, "Ansökan mottagen - #" + applicationId,
                "Din kreditansökan har mottagits och behandlas.\n\n"
                        + "Ansöknings-ID: " + applicationId + "\n\n"
                        + "Vi meddelar dig när ett beslut har tagits.");
    }

    public void sendStatusUpdate(String to, Long applicationId, String newStatus) {
        emailProvider.send(to, "Ansökningsstatus uppdaterad - #" + applicationId,
                "Statusen för din kreditansökan har uppdaterats.\n\n"
                        + "Ansöknings-ID: " + applicationId + "\n"
                        + "Ny status: " + newStatus + "\n\n"
                        + "Vi granskar din ansökan och uppdaterar dig inom kort.");
    }

    public void sendDecision(String to, Long applicationId, String decision, String reason) {
        String subject = "Ansökan " + decision + " - #" + applicationId;
        String body = "Ett beslut har tagits angående din kreditansökan.\n\n"
                + "Ansöknings-ID: " + applicationId + "\n"
                + "Beslut: " + decision + "\n";
        if (reason != null && !reason.isBlank()) {
            body += "Motivering: " + reason + "\n";
        }
        body += "\nTack för din ansökan.";
        emailProvider.send(to, subject, body);
    }
}