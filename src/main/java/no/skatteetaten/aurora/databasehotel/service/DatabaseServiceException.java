package no.skatteetaten.aurora.databasehotel.service;

public class DatabaseServiceException extends RuntimeException {

    public DatabaseServiceException(String format) {
        super(format);
    }
}
