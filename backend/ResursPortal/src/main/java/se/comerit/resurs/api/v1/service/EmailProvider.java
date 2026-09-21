package se.comerit.resurs.api.v1.service;

public interface EmailProvider {
    void send(String to, String subject, String body);
}