package se.comerit.resurs.config;

import org.springframework.stereotype.Component;

import se.comerit.resurs.api.v1.service.ResursCryptoService;

/**
 * Holds the active {@link ResursCryptoService} for static call sites such as
 * repository default methods, so callers can stay free of cryptographic
 * concerns (e.g. {@code companyRepository.findByOrgNumber("...")}).
 */
@Component
public class PiiIndexProvider {

    private static ResursCryptoService crypto;

    public PiiIndexProvider(ResursCryptoService crypto) {
        PiiIndexProvider.crypto = crypto;
    }

    public static byte[] of(String value) {
        return crypto.blindIndex(value);
    }
}