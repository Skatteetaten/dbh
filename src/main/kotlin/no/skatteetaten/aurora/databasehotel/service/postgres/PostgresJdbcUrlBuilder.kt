package no.skatteetaten.aurora.databasehotel.service.postgres

import no.skatteetaten.aurora.databasehotel.service.JdbcUrlBuilder

class PostgresJdbcUrlBuilder : JdbcUrlBuilder {

    override fun create(dbHost: String, port: Int, database: String?): String =
        "jdbc:postgresql://$dbHost:$port/$database"
}
