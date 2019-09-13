package no.skatteetaten.aurora.databasehotel.service

import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.dto.Label
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema.Type
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema.Type.MANAGED
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData
import no.skatteetaten.aurora.databasehotel.domain.User
import no.skatteetaten.aurora.databasehotel.service.LookupDataFetchStrategy.BY_ALL
import no.skatteetaten.aurora.databasehotel.service.LookupDataFetchStrategy.FOR_EACH

enum class LookupDataFetchStrategy { BY_ALL, FOR_EACH }

data class LookupData(
    private val metaInfo: DatabaseInstanceMetaInfo,
    private val jdbcUrlBuilder: JdbcUrlBuilder,
    val users: List<SchemaUser>,
    val schemas: List<Schema>,
    val labels: List<Label>,
    val schemaSizes: List<SchemaSize>
) {
    val schemaIndex = schemas.map { it.username to it }.toMap()
    val userIndex = users.map { it.schemaId!! to it }.toMap()
    val labelIndex = labels.groupBy { it.schemaId!! }
    val schemaSizeIndex = schemaSizes.map { it.owner to it }.toMap()

    fun createMany(schemaDataList: List<SchemaData>, type: Type = MANAGED) = schemaDataList
        .filter { schemaIndex.containsKey(it.name) }
        .map { createOne(it, type) }.toSet()

    fun createOne(schemaData: SchemaData, type: Type = MANAGED): DatabaseSchema {

        val schema = schemaIndex[schemaData.name]
            ?: error("Missing SchemaData for Schema ${schemaData.name}")

        val schemaUsers = listOfNotNull(userIndex[schemaData.id])
        val schemaLabels = labelIndex[schemaData.id] ?: emptyList()
        val schemaSize = schemaSizeIndex[schema.username]
            ?: error("Missing SchemaSize for Schema ${schema.username}")

        return createOne(schemaData, schema, schemaUsers, schemaLabels, schemaSize, type)
    }

    private fun createOne(
        schemaData: SchemaData,
        schema: Schema,
        users: List<SchemaUser>,
        labels: List<Label>,
        schemaSize: SchemaSize,
        type: Type = MANAGED
    ): DatabaseSchema {

        val jdbcUrl = jdbcUrlBuilder.create(metaInfo.host, metaInfo.port, schema.username)

        val databaseSchema = DatabaseSchema(
            schemaData.id,
            metaInfo,
            jdbcUrl,
            schema.username,
            schema.created,
            schema.lastLogin,
            createMetaData(schemaSize),
            type
        )
        users.forEach { databaseSchema.addUser(User(it.id!!, it.username!!, it.password!!, it.type!!)) }
        databaseSchema.labels = labels.map { it.name!! to it.value }.toMap()

        return databaseSchema
    }

    companion object {

        @JvmStatic
        private fun createMetaData(schemaSize: SchemaSize?): DatabaseSchemaMetaData =
            DatabaseSchemaMetaData(schemaSize?.schemaSizeMb?.toDouble() ?: 0.0)
    }
}

class DatabaseSchemaBuilder(
    private val metaInfo: DatabaseInstanceMetaInfo,
    private val jdbcUrlBuilder: JdbcUrlBuilder,
    private val databaseHotelDataDao: DatabaseHotelDataDao,
    private val databaseManager: DatabaseManager,
    private val resourceUsageCollector: ResourceUsageCollector
) {

    fun build(schemaData: SchemaData): DatabaseSchema? =
        build(listOf(schemaData), FOR_EACH).firstOrNull()

    fun build(schemaData: List<SchemaData>, strategy: LookupDataFetchStrategy = BY_ALL): Set<DatabaseSchema> {

        val lookupData = when (strategy) {

            BY_ALL -> LookupData(
                metaInfo,
                jdbcUrlBuilder,
                databaseHotelDataDao.findAllUsers(),
                databaseManager.findAllNonSystemSchemas(),
                databaseHotelDataDao.findAllLabels(),
                resourceUsageCollector.schemaSizes
            )
            FOR_EACH -> LookupData(
                metaInfo,
                jdbcUrlBuilder,
                schemaData.flatMap { databaseHotelDataDao.findAllUsersForSchema(it.id) },
                schemaData.mapNotNull { databaseManager.findSchemaByName(it.name) },
                schemaData.flatMap { databaseHotelDataDao.findAllLabelsForSchema(it.id) },
                resourceUsageCollector.schemaSizes
            )
        }
        return lookupData.createMany(schemaData)
    }
}
