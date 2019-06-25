package no.skatteetaten.aurora.databasehotel.service

import no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.SchemaTypes.SCHEMA_TYPE_EXTERNAL
import no.skatteetaten.aurora.databasehotel.dao.dto.ExternalSchema
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema.Type.EXTERNAL
import no.skatteetaten.aurora.databasehotel.service.DatabaseInstance.UserType.SCHEMA
import no.skatteetaten.aurora.databasehotel.service.internal.DatabaseSchemaBuilder
import java.math.BigDecimal
import java.util.HashMap
import java.util.Optional

class ExternalSchemaManager(private val databaseHotelDataDao: DatabaseHotelDataDao) {

    fun findSchemaById(schemaId: String): DatabaseSchema? {

        return databaseHotelDataDao.findSchemaDataById(schemaId)
            ?.let(this::getDatabaseSchemaFromSchemaData)
    }

    fun findAllSchemas(): Set<DatabaseSchema> {

        // TODO: Iterating like this may (will) become a performance bottleneck at some point.
        return databaseHotelDataDao.findAllSchemaDataBySchemaType(SCHEMA_TYPE_EXTERNAL)
            .map(this::getDatabaseSchemaFromSchemaData).toSet()
    }

    fun registerSchema(
        username: String,
        password: String,
        jdbcUrl: String,
        labelMap: Map<String, String?>
    ): DatabaseSchema = databaseHotelDataDao.run {
        val schemaData = createSchemaData(username, SCHEMA_TYPE_EXTERNAL)
        val externalSchema = registerExternalSchema(schemaData.id, jdbcUrl)
        replaceLabels(schemaData.id, labelMap)
        val user = createUser(schemaData.id, SCHEMA.toString(), username, password)
        createDatabaseSchema(schemaData, externalSchema, listOf(user))
    }

    fun deleteSchema(schemaId: String) = databaseHotelDataDao.run {
        deleteSchemaData(schemaId)
        deleteUsersForSchema(schemaId)
        deleteLabelsForSchema(schemaId)
        deleteExternalSchema(schemaId)
    }

    fun replaceLabels(schema: DatabaseSchema, labels: Map<String, String?>) {

        schema.labels = labels
        databaseHotelDataDao.replaceLabels(schema.id, labels)
    }

    fun updateConnectionInfo(schemaId: String, username: String?, jdbcUrl: String?, password: String?) {

        databaseHotelDataDao.updateExternalSchema(schemaId, username, jdbcUrl, password)
    }

    private fun createDatabaseSchema(
        schemaData: SchemaData,
        externalSchema: ExternalSchema,
        users: List<SchemaUser>
    ): DatabaseSchema {

        val schema = Schema(schemaData.name, externalSchema.createdDate!!)
        val labels = databaseHotelDataDao.findAllLabelsForSchema(schemaData.id)
        val metaInfo = DatabaseInstanceMetaInfo(ORACLE, "external", "-", 0, false, HashMap())

        return DatabaseSchemaBuilder(metaInfo, externalSchema::jdbcUrl as JdbcUrlBuilder)
            .createOne(
                schemaData, schema, users,
                Optional.ofNullable(labels),
                SchemaSize(schemaData.name, BigDecimal.ZERO),
                EXTERNAL
            )
    }

    private fun getDatabaseSchemaFromSchemaData(schemaData: SchemaData): DatabaseSchema {

        schemaData.takeIf { it.schemaType == SCHEMA_TYPE_EXTERNAL }
            ?: throw DatabaseServiceException("Schema with id ${schemaData.id} is not an external schema")
        val externalSchema = databaseHotelDataDao.findExternalSchemaById(schemaData.id)
            ?: throw DatabaseServiceException("Schema with id ${schemaData.id} is an external schema but no connection info found")
        val users = databaseHotelDataDao.findAllUsersForSchema(schemaData.id)

        return createDatabaseSchema(schemaData, externalSchema, users)
    }
}
