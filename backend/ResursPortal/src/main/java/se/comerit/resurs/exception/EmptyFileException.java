package se.comerit.resurs.exception;

public class EmptyFileException extends RuntimeException {
    public EmptyFileException() {
        super("No file found please, Upload a new file!.");
    }
}
