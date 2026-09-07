package se.comerit.resurs.exception;

/**
 * CompanyNotFoundException
 */
public class CompanyNotFoundException extends RuntimeException {

    public CompanyNotFoundException(String orgNumber) {
        super("Company not found: orgNumber=" + orgNumber);
    }

}
