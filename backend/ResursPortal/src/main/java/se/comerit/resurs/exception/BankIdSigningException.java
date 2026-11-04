package se.comerit.resurs.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A BankID signing order could not be completed: declined by the signatory,
 * timed out, or the provider was unreachable.
 *
 * <p>
 * Mapped to 403 rather than 401 because the caller is already authenticated --
 * the session is fine, it is the signing step that did not succeed. The message
 * is fixed and carries no organisation number, so it cannot be used to probe
 * which organisations exist.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class BankIdSigningException extends RuntimeException {

    public BankIdSigningException(String message) {
        super(message);
    }

    public BankIdSigningException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The one message the API returns, identical for every failure cause. */
    public static BankIdSigningException signingFailed() {
        return new BankIdSigningException("BankID-signering misslyckades");
    }
}
