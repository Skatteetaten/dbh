package no.skatteetaten.aurora.databasehotel.service

interface JdbcUrlBuilder {

    fun create(dbHost: String, port: Int, database: String?): String
}
