package se.comerit.resurs.api.v1.service;

public interface EmailService {
    void sendApplicationSubmitted(String to, Long applicationId);
    void sendStatusUpdate(String to, Long applicationId, String newStatus);
    void sendDecision(String to, Long applicationId, String decision, String reason);
}
