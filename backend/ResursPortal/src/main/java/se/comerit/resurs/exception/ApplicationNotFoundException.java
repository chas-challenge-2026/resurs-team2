package se.comerit.resurs.exception;

public class ApplicationNotFoundException extends RuntimeException {
    public ApplicationNotFoundException(Long applicationId) {
        super("Application with ID " + applicationId + " not found.");

    }
}
