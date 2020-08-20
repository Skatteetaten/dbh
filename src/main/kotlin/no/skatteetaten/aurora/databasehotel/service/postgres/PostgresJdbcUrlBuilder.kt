package no.skatteetaten.aurora.databasehotel.service.postgres

import mu.KotlinLogging
import no.skatteetaten.aurora.databasehotel.service.JdbcUrlBuilder

private val logger = KotlinLogging.logger {}

class PostgresJdbcUrlBuilder : JdbcUrlBuilder {

    override fun create(dbHost: String, port: Int, database: String?): String {
        val jdbc = if (dbHost.contains("azure")) {
            "jdbc:postgresql://$dbHost:$port/$database?ssl=true&sslmode=require"
        } else {
            "jdbc:postgresql://$dbHost:$port/$database"
        }
        logger.info("Jdbc url is $jdbc")
        return jdbc
    }
}
