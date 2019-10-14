package no.skatteetaten.aurora.databasehotel.web.rest

import no.skatteetaten.aurora.databasehotel.service.DatabaseServiceException

class OperationDisabledException(format: String) : DatabaseServiceException(format)
