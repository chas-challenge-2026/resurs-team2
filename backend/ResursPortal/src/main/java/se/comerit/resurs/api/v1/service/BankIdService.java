package se.comerit.resurs.api.v1.service;

public interface BankIdService {
    /**
     * @return true if the org was successfully authenticated via BankID
     */
    boolean authenticate(String orgNumber);
}
