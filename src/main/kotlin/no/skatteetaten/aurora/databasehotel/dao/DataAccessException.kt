package no.skatteetaten.aurora.databasehotel.dao

class DataAccessException : RuntimeException {

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}
