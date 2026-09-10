package se.comerit.resurs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import se.comerit.resurs.config.PiiIndexProvider;
import se.comerit.resurs.entity.CaseWorker;

public interface CaseWorkerRepository extends JpaRepository<CaseWorker, Long> {
    Optional<CaseWorker> findByEmailIndex(byte[] emailIndex);

    /**
     * Looks a case worker up by plaintext email: derives the blind index
     * (HMAC-SHA256 of the canonicalized value) and compares it against the
     * persisted index column, so no caller needs access to the crypto service.
     */
    default Optional<CaseWorker> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return findByEmailIndex(PiiIndexProvider.of(email));
    }
}
