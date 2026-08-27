package se.comerit.resurs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ResursPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResursPortalApplication.class, args);
    }
}
