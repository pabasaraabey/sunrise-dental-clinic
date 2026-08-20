package lk.sdcms.exception;

/** A business rule was violated by otherwise well-formed input. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
