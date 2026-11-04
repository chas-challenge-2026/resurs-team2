package se.comerit.resurs.exception;

public class CryptoException extends RuntimeException {

    private final int errorCode;

    public CryptoException(int errorCode) {
        super("Native crypto error: code=" + errorCode + " (" + describe(errorCode) + ")");
        this.errorCode = errorCode;
    }

    public CryptoException(String message) {
        super(message);
        this.errorCode = 0;
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 0;
    }

    public int getErrorCode() {
        return errorCode;
    }

    private static String describe(int code) {
        return switch (code) {
            case -1 -> "NOT_INIT";
            case -2 -> "AUTH";
            case -3 -> "KEY_IO";
            case -4 -> "BUFFER_SMALL";
            case -5 -> "INVALID_ARG";
            case -6 -> "INTERNAL";
            case -7 -> "KEY_VERSION";
            default -> "UNKNOWN";
        };
    }
}
