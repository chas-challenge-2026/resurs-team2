package se.comerit.resurs.exception;

public class ApplicationAlreadyDecidedException extends RuntimeException {

    public ApplicationAlreadyDecidedException(Long id) {
        super("Application already decided: id=" + id);
    }

}
