package no.skatteetaten.aurora.databasehotel.service.oracle

import no.skatteetaten.aurora.databasehotel.service.JdbcUrlBuilder

class OracleJdbcUrlBuilder(private val service: String) : JdbcUrlBuilder {

    override fun create(dbHost: String, port: Int, database: String?): String =
        "jdbc:oracle:thin:@$dbHost:$port/$service"
}
