package no.skatteetaten.aurora.databasehotel.service

import com.google.common.collect.Lists
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

class ExternalSchemaManager(private val databaseHotelDataDao: DatabaseHotelDataDao) {

    fun findSchemaById(schemaId: String): DatabaseSchema? {

        return databaseHotelDataDao.findSchemaDataById(schemaId)
            .takeIf { it?.schemaType == SCHEMA_TYPE_EXTERNAL }?.let {
                val externalSchema = databaseHotelDataDao.findExternalSchemaById(schemaId)
                    ?: throw DatabaseServiceException("Could not find ExternalSchema data for schema with id $schemaId")
                val users = databaseHotelDataDao.findAllUsersForSchema(schemaId)
                createDatabaseSchema(it, externalSchema, users)
            }
    }

    fun findAllSchemas(): Set<DatabaseSchema> {

        val externalSchemas = databaseHotelDataDao.findAllSchemaDataBySchemaType(SCHEMA_TYPE_EXTERNAL)
        // TODO: Iterating like this may (will) become a performance bottleneck at some point.
        return externalSchemas.map { schemaData ->
            val id = schemaData.id
            val externalSchema = databaseHotelDataDao.findExternalSchemaById(id)
                ?: throw DatabaseServiceException("Could not find ExternalSchema data for schema with id $id")
            val users = databaseHotelDataDao.findAllUsersForSchema(id)
            createDatabaseSchema(schemaData, externalSchema, users)
        }.toSet()
    }

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

    fun replaceLabels(schema: DatabaseSchema, labels: Map<String, String?>) {

        schema.labels = labels
        databaseHotelDataDao.replaceLabels(schema.id, labels)
    }

    fun updateConnectionInfo(schemaId: String, username: String?, jdbcUrl: String?, password: String?) {

        databaseHotelDataDao.updateExternalSchema(schemaId, username, jdbcUrl, password)
    }
}
