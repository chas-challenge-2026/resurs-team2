package se.comerit.resurs.api.v1.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "resurs.email.provider", havingValue = "smtp", matchIfMissing = false)
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;

    @Value("${resurs.email.from:noreply@resurs.se}")
    private String fromAddress;

    public SmtpEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendApplicationSubmitted(String to, Long applicationId) {
        send(to, "Application Received - #" + applicationId,
                "Your credit application has been received and is being processed.\n\n"
                        + "Application ID: " + applicationId + "\n\n"
                        + "We will notify you once a decision has been made.");
    }

    @Override
    public void sendStatusUpdate(String to, Long applicationId, String newStatus) {
        send(to, "Application Status Updated - #" + applicationId,
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
        send(to, subject, body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {} with subject '{}'", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {} with subject '{}': {}", to, subject, e.getMessage());
        }
    }
}
