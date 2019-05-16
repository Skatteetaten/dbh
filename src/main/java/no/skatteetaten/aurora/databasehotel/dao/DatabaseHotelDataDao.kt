package no.skatteetaten.aurora.databasehotel.dao

import java.util.Optional

import no.skatteetaten.aurora.databasehotel.dao.dto.ExternalSchema
import no.skatteetaten.aurora.databasehotel.dao.dto.Label
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser

interface DatabaseHotelDataDao {

    fun createSchemaData(name: String): SchemaData

    fun createSchemaData(name: String, schemaType: String): SchemaData

    fun findSchemaDataById(id: String): SchemaData?

    fun findSchemaDataByName(name: String): Optional<SchemaData>

    fun deleteSchemaData(id: String)

    fun deactivateSchemaData(id: String)

    fun findAllManagedSchemaData(): List<SchemaData>

    fun findAllSchemaDataBySchemaType(schemaType: String): List<SchemaData>

    fun findAllManagedSchemaDataByLabels(labels: Map<String, String?>): List<SchemaData>

    fun createUser(schemaId: String, userType: String, username: String, password: String): SchemaUser

    fun findUserById(id: String): Optional<SchemaUser>

    fun findAllUsers(): List<SchemaUser>

    fun findAllUsersForSchema(schemaId: String): List<SchemaUser>

    fun deleteUsersForSchema(schemaId: String)

    fun updateUserPassword(schemaId: String, password: String)

    fun findAllLabels(): List<Label>

    fun findAllLabelsForSchema(schemaId: String): List<Label>

    fun replaceLabels(schemaId: String, labels: Map<String, String>)

    fun deleteLabelsForSchema(schemaId: String)

    fun registerExternalSchema(id: String, jdbcUrl: String): ExternalSchema

    fun findExternalSchemaById(id: String): Optional<ExternalSchema>

    fun deleteExternalSchema(schemaId: String)

    fun updateExternalSchema(schemaId: String, username: String, jdbcUrl: String, password: String)
}

object SchemaTypes {

    const val SCHEMA_TYPE_MANAGED = "MANAGED"

    const val SCHEMA_TYPE_EXTERNAL = "EXTERNAL"
}
