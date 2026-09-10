package se.comerit.resurs.entity;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import org.springframework.stereotype.Component;

import se.comerit.resurs.api.v1.service.ResursCryptoService;

/**
 * Maintains the company blind index (HMAC-SHA256 of the canonicalized org
 * number) so equality lookups never need the plaintext.
 */
@Component
public class CompanyBlindIndexListener {

    private final ResursCryptoService crypto;

    public CompanyBlindIndexListener(ResursCryptoService crypto) {
        this.crypto = crypto;
    }

    @PrePersist
    @PreUpdate
    public void generateBlindIndex(Company company) {
        company.setOrgNumberIndex(crypto.blindIndex(company.getOrgNumber()));
    }
}