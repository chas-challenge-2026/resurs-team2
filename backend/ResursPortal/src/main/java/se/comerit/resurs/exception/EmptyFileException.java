package se.comerit.resurs.exception;

public class EmptyFileException extends RuntimeException {
    public EmptyFileException() {
        super("Ingen fil skickades. Vänligen välj en fil att ladda upp.");
    }
}
