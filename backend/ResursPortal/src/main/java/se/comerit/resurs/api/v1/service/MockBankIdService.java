package se.comerit.resurs.api.v1.service;

import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class MockBankIdService implements BankIdService {

    private static final Set<String> WHITELIST = Set.of("556000-1234", "556000-5678");

    @Override
    public boolean authenticate(String orgNumber) {
        return WHITELIST.contains(orgNumber);
    }
    
}
