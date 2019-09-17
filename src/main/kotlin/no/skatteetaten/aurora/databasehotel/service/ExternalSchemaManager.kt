package no.skatteetaten.aurora.databasehotel.service

import com.google.common.collect.Lists
import java.math.BigDecimal
import java.util.HashMap
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

class ExternalSchemaManager(private val databaseHotelDataDao: DatabaseHotelDataDao) {

    fun findSchemaById(schemaId: String): DatabaseSchema? =
        databaseHotelDataDao.findSchemaDataById(schemaId)
            ?.takeIf { it.schemaType == SCHEMA_TYPE_EXTERNAL }
            ?.let(this::getDatabaseSchemaFromSchemaData)

    fun findAllSchemas(): Set<DatabaseSchema> =
        databaseHotelDataDao.findAllSchemaDataBySchemaType(SCHEMA_TYPE_EXTERNAL)
            // TODO: Iterating like this may (will) become a performance bottleneck at some point.
            .map(this::getDatabaseSchemaFromSchemaData).toSet()

    fun registerSchema(
        username: String,
        password: String,
        jdbcUrl: String,
        labelMap: Map<String, String?>
    ): DatabaseSchema {

        val schemaData = databaseHotelDataDao.createSchemaData(username, SCHEMA_TYPE_EXTERNAL)
        val externalSchema = databaseHotelDataDao.registerExternalSchema(schemaData.id, jdbcUrl)
        databaseHotelDataDao.replaceLabels(schemaData.id, labelMap)
        val user = databaseHotelDataDao.createUser(schemaData.id, SCHEMA.toString(), username, password)
        val users = Lists.newArrayList(user)

        return createDatabaseSchema(schemaData, externalSchema, users)
    }

    fun deleteSchema(schemaId: String) {

        databaseHotelDataDao.deleteSchemaData(schemaId)
        databaseHotelDataDao.deleteUsersForSchema(schemaId)
        databaseHotelDataDao.deleteLabelsForSchema(schemaId)
        databaseHotelDataDao.deleteExternalSchema(schemaId)
    }

    fun updateSchema(
        schema: DatabaseSchema,
        labels: Map<String, String?>,
        username: String?,
        password: String?
    ): DatabaseSchema {
        replaceLabels(schema, labels)
        updateConnectionInfo(schema.id, username, schema.jdbcUrl, password)
        return findSchemaById(schema.id) ?: throw IllegalStateException("Unable to find schema ${schema.id}")
    }

    private fun createDatabaseSchema(
        schemaData: SchemaData,
        externalSchema: ExternalSchema,
        users: List<SchemaUser>
    ): DatabaseSchema {

        val schema = Schema(schemaData.name, externalSchema.createdDate!!)

        val labels = databaseHotelDataDao.findAllLabelsForSchema(schemaData.id)

        val metaInfo = DatabaseInstanceMetaInfo(ORACLE, "external", "-", 0, false, HashMap())
        val jdbcUrlBuilder = object : JdbcUrlBuilder {
            override fun create(dbHost: String, port: Int, database: String?): String {
                return externalSchema.jdbcUrl ?: ""
            }
        }

        return DatabaseSchemaBuilder(metaInfo, jdbcUrlBuilder).createOne(
            schemaData, schema, users, labels,
            SchemaSize(schemaData.name, BigDecimal.ZERO),
            EXTERNAL
        )
    }

    private fun replaceLabels(schema: DatabaseSchema, labels: Map<String, String?>) {

        schema.labels = labels
        databaseHotelDataDao.replaceLabels(schema.id, labels)
    }

    private fun updateConnectionInfo(schemaId: String, username: String?, jdbcUrl: String?, password: String?) {

        databaseHotelDataDao.updateExternalSchema(schemaId, username, jdbcUrl, password)
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
