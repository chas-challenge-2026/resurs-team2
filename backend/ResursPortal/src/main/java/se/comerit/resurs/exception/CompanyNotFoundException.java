package se.comerit.resurs.exception;

/**
 * CompanyNotFoundException
 *
 * <p>
 * Deliberately carries no identifying information (no organisation number, no
 * caller-supplied value) so that neither log output nor exception messages can
 * reveal whether a company is registered.
 */
public class CompanyNotFoundException extends RuntimeException {

    public CompanyNotFoundException() {
        super("Company not found");
    }

}