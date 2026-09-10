package se.comerit.resurs.api.v1.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "resurs.email.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailService implements EmailService {

    @Override
    public void sendApplicationSubmitted(String to, Long applicationId) {
        printEmail(to, "Application Received - #" + applicationId,
                "Your credit application has been received and is being processed.\n\n"
                        + "Application ID: " + applicationId + "\n\n"
                        + "We will notify you once a decision has been made.");
    }

    @Override
    public void sendStatusUpdate(String to, Long applicationId, String newStatus) {
        printEmail(to, "Application Status Updated - #" + applicationId,
                "Your credit application status has been updated.\n\n"
                        + "Application ID: " + applicationId + "\n"
                        + "New Status: " + newStatus + "\n\n"
                        + "We are reviewing your application and will update you shortly.");
    }

    @Override
    public void sendDecision(String to, Long applicationId, String decision, String reason) {
        String subject = "Application " + decision + " - #" + applicationId;
        String body = "A decision has been made on your credit application.\n\n"
                + "Application ID: " + applicationId + "\n"
                + "Decision: " + decision + "\n";
        if (reason != null && !reason.isBlank()) {
            body += "Reason: " + reason + "\n";
        }
        body += "\nThank you for your application.";
        printEmail(to, subject, body);
    }

    private void printEmail(String to, String subject, String body) {
        System.out.println();
        System.out.println("========================================");
        System.out.println("       EMAIL NOTIFICATION (CONSOLE)     ");
        System.out.println("========================================");
        System.out.println("To:      " + to);
        System.out.println("Subject: " + subject);
        System.out.println("----------------------------------------");
        System.out.println(body);
        System.out.println("========================================");
        System.out.println();
    }
}
