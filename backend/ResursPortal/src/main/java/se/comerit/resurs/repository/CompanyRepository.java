package se.comerit.resurs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import se.comerit.resurs.config.PiiIndexProvider;
import se.comerit.resurs.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByOrgNumberIndex(byte[] orgNumberIndex);

    /**
     * Looks a company up by plaintext org number: derives the blind index
     * (HMAC-SHA256 of the canonicalized value) and compares it against the
     * persisted index column, so no caller needs access to the crypto service.
     */
    default Optional<Company> findByOrgNumber(String orgNumber) {
        if (orgNumber == null) {
            return Optional.empty();
        }
        return findByOrgNumberIndex(PiiIndexProvider.of(orgNumber));
    }
}