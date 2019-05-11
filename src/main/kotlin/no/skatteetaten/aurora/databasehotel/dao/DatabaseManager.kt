package no.skatteetaten.aurora.databasehotel.dao

import java.util.*

data class Schema @JvmOverloads constructor(
        val username: String,
        val created: Date,
        val lastLogin: Date? = null
)

interface DatabaseManager {

    fun schemaExists(schemaName: String): Boolean

    fun createSchema(schemaName: String, password: String): String

    fun updatePassword(schemaName: String, password: String)

    fun findSchemaByName(schemaName: String): Optional<Schema>

    fun findAllNonSystemSchemas(): List<Schema>

    fun deleteSchema(schemaName: String)

    fun executeStatements(vararg statements: String)
}
