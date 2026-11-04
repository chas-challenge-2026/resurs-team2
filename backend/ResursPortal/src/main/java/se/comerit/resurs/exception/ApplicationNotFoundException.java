package se.comerit.resurs.exception;

/**
 * ApplicationNotFoundException
 */
public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(Long id) {
        super("Application not found: id=" + id);
    }

}
