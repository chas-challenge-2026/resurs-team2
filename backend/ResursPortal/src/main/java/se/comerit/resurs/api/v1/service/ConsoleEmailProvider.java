package se.comerit.resurs.api.v1.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "resurs.email.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailProvider implements EmailProvider {

    @Override
    public void send(String to, String subject, String body) {
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