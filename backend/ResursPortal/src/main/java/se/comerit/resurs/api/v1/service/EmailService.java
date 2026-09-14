package se.comerit.resurs.api.v1.service;

import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final EmailProvider emailProvider;

    public EmailService(EmailProvider emailProvider) {
        this.emailProvider = emailProvider;
    }

    public void sendApplicationSubmitted(String to, Long applicationId) {
        emailProvider.send(to, "Application Received - #" + applicationId,
                "Your credit application has been received and is being processed.\n\n"
                        + "Application ID: " + applicationId + "\n\n"
                        + "We will notify you once a decision has been made.");
    }

    public void sendStatusUpdate(String to, Long applicationId, String newStatus) {
        emailProvider.send(to, "Application Status Updated - #" + applicationId,
                "Your credit application status has been updated.\n\n"
                        + "Application ID: " + applicationId + "\n"
                        + "New Status: " + newStatus + "\n\n"
                        + "We are reviewing your application and will update you shortly.");
    }

    public void sendDecision(String to, Long applicationId, String decision, String reason) {
        to = to.replace(' ', '_');
        String subject = "Application " + decision + " - #" + applicationId;
        String body = "A decision has been made on your credit application.\n\n"
                + "Application ID: " + applicationId + "\n"
                + "Decision: " + decision + "\n";
        if (reason != null && !reason.isBlank()) {
            body += "Reason: " + reason + "\n";
        }
        body += "\nThank you for your application.";
        emailProvider.send(to + "@example.com", subject, body);
    }
}