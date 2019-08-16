package no.skatteetaten.aurora.databasehotel.web.rest;

import no.skatteetaten.aurora.databasehotel.service.DatabaseServiceException;

public class OperationDisabledException extends DatabaseServiceException {

    public OperationDisabledException(String format) {
        super(format);
    }
}
