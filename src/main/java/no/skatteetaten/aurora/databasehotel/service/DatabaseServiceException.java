package no.skatteetaten.aurora.databasehotel.service;

public class DatabaseServiceException extends RuntimeException {

    public DatabaseServiceException(String format) {
        super(format);
    }

    public DatabaseServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
