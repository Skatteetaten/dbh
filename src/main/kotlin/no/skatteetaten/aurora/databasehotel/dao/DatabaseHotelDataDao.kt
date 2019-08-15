package no.skatteetaten.aurora.databasehotel.dao

import java.time.Duration
import java.util.Date
import no.skatteetaten.aurora.databasehotel.dao.dto.ExternalSchema
import no.skatteetaten.aurora.databasehotel.dao.dto.Label
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser

interface DatabaseHotelDataDao {

    fun createSchemaData(name: String): SchemaData

    fun createSchemaData(name: String, schemaType: String): SchemaData

    fun findSchemaDataById(id: String, active: Boolean = true): SchemaData?

    fun findSchemaDataByName(name: String): SchemaData?

    fun findSchemaDataByNameIgnoreActive(name: String): SchemaData?

    fun deleteSchemaData(id: String)

    fun deactivateSchemaData(id: String, cooldownDuration: Duration)

    fun findAllManagedSchemaData(): List<SchemaData>

    fun findAllSchemaDataBySchemaType(schemaType: String): List<SchemaData>

    fun findAllManagedSchemaDataByLabels(labels: Map<String, String?>): List<SchemaData>

    fun findAllManagedSchemaDataIgnoreActive(): List<SchemaData>

    fun findAllManagedSchemaDataByDeleteAfterDate(deleteAfter: Date): List<SchemaData>

    fun createUser(schemaId: String, userType: String, username: String, password: String): SchemaUser

    fun findUserById(id: String): SchemaUser?

    fun findAllUsers(): List<SchemaUser>

    fun findAllUsersForSchema(schemaId: String): List<SchemaUser>

    fun deleteUsersForSchema(schemaId: String)

    fun updateUserPassword(schemaId: String, password: String)

    fun findAllLabels(): List<Label>

    fun findAllLabelsForSchema(schemaId: String): List<Label>

    fun replaceLabels(schemaId: String, labels: Map<String, String?>)

    fun deleteLabelsForSchema(schemaId: String)

    fun registerExternalSchema(id: String, jdbcUrl: String): ExternalSchema

    fun findExternalSchemaById(id: String): ExternalSchema?

    fun deleteExternalSchema(schemaId: String)

    fun updateExternalSchema(schemaId: String, username: String?, jdbcUrl: String?, password: String?)
}

object SchemaTypes {

    const val SCHEMA_TYPE_MANAGED = "MANAGED"

    const val SCHEMA_TYPE_EXTERNAL = "EXTERNAL"
}
