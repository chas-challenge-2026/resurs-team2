package se.comerit.resurs.entity;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import org.springframework.stereotype.Component;

import se.comerit.resurs.api.v1.service.ResursCryptoService;

/**
 * Maintains the case worker email blind index (HMAC-SHA256 of the canonicalized
 * email) so equality lookups never need the plaintext.
 */
@Component
public class CaseWorkerBlindIndexListener {

    private final ResursCryptoService crypto;

    public CaseWorkerBlindIndexListener(ResursCryptoService crypto) {
        this.crypto = crypto;
    }

    @PrePersist
    @PreUpdate
    public void generateBlindIndex(CaseWorker worker) {
        worker.setEmailIndex(crypto.blindIndex(worker.getEmail()));
    }
}