package se.comerit.resurs.config;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.comerit.resurs.api.v1.service.LoginRateLimiterService;


@Configuration
public class LoginRateLimiterConfig {


    @Bean
    public LoginRateLimiterService loginRateLimiter(
            @Value("${resurs.login-rate-limit.email-limit:5}") int emailLimit,
            @Value("${resurs.login-rate-limit.ip-limit:20}") int ipLimit) {
        return new LoginRateLimiterService(emailLimit, ipLimit);
    }
}


