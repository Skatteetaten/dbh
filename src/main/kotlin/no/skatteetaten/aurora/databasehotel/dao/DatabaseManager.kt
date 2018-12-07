package no.skatteetaten.aurora.databasehotel.dao

import java.util.Optional

import javax.sql.DataSource

import no.skatteetaten.aurora.databasehotel.dao.dto.Schema

interface DatabaseManager {

    fun schemaExists(schemaName: String): Boolean

    fun createSchema(schemaName: String, password: String): String

    fun updatePassword(schemaName: String, password: String)

    fun findSchemaByName(name: String): Optional<Schema>

    fun findAllNonSystemSchemas(): List<Schema>

    fun deleteSchema(schemaName: String)

    fun executeStatements(vararg statements: String)
}
