package lk.sdcms.exception;

/**
 * Wraps SQLException so that callers of the DAO layer are not forced to import
 * java.sql. Keeping JDBC types out of the service layer is what allows the
 * storage mechanism to change without rewriting business logic.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
