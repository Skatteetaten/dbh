package no.skatteetaten.aurora.databasehotel.dao

import java.util.Date

data class Schema @JvmOverloads constructor(
    val username: String,
    val lastLogin: Date? = null
)

interface DatabaseManager {

    fun schemaExists(schemaName: String): Boolean

    fun createSchema(schemaName: String, password: String): String

    fun updatePassword(schemaName: String, password: String)

    fun findSchemaByName(schemaName: String): Schema?

    fun findAllNonSystemSchemas(): List<Schema>

    fun deleteSchema(schemaName: String)

    fun executeStatements(vararg statements: String)

    fun getMaxTablespaces(): Int?

    fun getUsedTablespaces(): Int?
}
