package no.skatteetaten.aurora.databasehotel.service

import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.dto.Label
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.LookupDataFetchStrategy.BY_ALL
import no.skatteetaten.aurora.databasehotel.service.LookupDataFetchStrategy.FOR_EACH
import no.skatteetaten.aurora.databasehotel.service.internal.DatabaseSchemaBuilder

enum class LookupDataFetchStrategy { BY_ALL, FOR_EACH }

private data class LookupData(
    val users: List<SchemaUser>,
    val schemas: List<Schema>,
    val labels: List<Label>,
    val schemaSizes: List<SchemaSize>
)

class DatabaseSchemaBuilder2(
    private val metaInfo: DatabaseInstanceMetaInfo,
    private val jdbcUrlBuilder: JdbcUrlBuilder,
    private val databaseHotelDataDao: DatabaseHotelDataDao,
    private val databaseManager: DatabaseManager,
    private val resourceUsageCollector: ResourceUsageCollector
) {

    fun build(schemaData: SchemaData): DatabaseSchema? =
        build(listOf(schemaData), FOR_EACH).firstOrNull()

    fun build(schemaData: List<SchemaData>, strategy: LookupDataFetchStrategy = BY_ALL): Set<DatabaseSchema> {

        val (users, schemas, labels, schemaSizes) = when (strategy) {

            BY_ALL -> LookupData(
                databaseHotelDataDao.findAllUsers(),
                databaseManager.findAllNonSystemSchemas(),
                databaseHotelDataDao.findAllLabels(),
                resourceUsageCollector.schemaSizes
            )
            FOR_EACH -> LookupData(
                schemaData.flatMap { databaseHotelDataDao.findAllUsersForSchema(it.id) },
                schemaData.mapNotNull { databaseManager.findSchemaByName(it.name) },
                schemaData.flatMap { databaseHotelDataDao.findAllLabelsForSchema(it.id) },
                resourceUsageCollector.schemaSizes
            )
        }
        return DatabaseSchemaBuilder(metaInfo, jdbcUrlBuilder)
            .createMany(schemaData, schemas, users, labels, schemaSizes)
    }
}