package no.skatteetaten.aurora.databasehotel.service

import mu.KotlinLogging
import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.SchemaTypes
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.ArrayList
import java.util.Calendar
import java.util.Date

private val logger = KotlinLogging.logger {}

enum class LookupDataFetchStrategy { BY_ALL, FOR_EACH }

open class DatabaseInstance(
    val metaInfo: DatabaseInstanceMetaInfo,
    val databaseManager: DatabaseManager,
    val databaseHotelDataDao: DatabaseHotelDataDao,
    val jdbcUrlBuilder: JdbcUrlBuilder,
    val resourceUsageCollector: ResourceUsageCollector,
    private val cooldownDaysAfterDelete: Int,
    private val cooldownDaysForOldUnusedSchemas: Int
) {

    private val integrations = ArrayList<Integration>()

    val isCreateSchemaAllowed: Boolean get() = metaInfo.createSchemaAllowed

    val instanceName: String get() = metaInfo.instanceName

    fun findSchemaById(id: String): DatabaseSchema? =
        databaseHotelDataDao.findSchemaDataById(id)?.let(this::getDatabaseSchemaFromSchemaData)

    fun findSchemaByName(name: String): DatabaseSchema? =
        databaseHotelDataDao.findSchemaDataByName(name)?.let(this::getDatabaseSchemaFromSchemaData)

    fun findAllSchemas(
        labelsToMatch: Map<String, String?>? = null,
        includeCooldown: Boolean = false
    ): Set<DatabaseSchema> {

        return if (labelsToMatch.isNullOrEmpty()) findAllSchemas(includeCooldown)
        else getDatabaseSchemaFromSchemaData(
            databaseHotelDataDao.findAllManagedSchemaDataByLabels(
                labelsToMatch,
                includeCooldown
            ), LookupDataFetchStrategy.FOR_EACH
        )
    }

    fun findAllSchemas(includeCooldown: Boolean = false): Set<DatabaseSchema> =
        getDatabaseSchemaFromSchemaData(databaseHotelDataDao.findAllManagedSchemaData(includeCooldown))

    @Transactional
    open fun createSchema(labels: Map<String, String?> = emptyMap()): DatabaseSchema {

        val (schemaName, password) = createSchemaNameAndPassword()
        return createSchema(schemaName, password, labels)
    }

    @Transactional
    open fun createSchema(schemaName: String, labels: Map<String, String?>): DatabaseSchema {

        val (_, password) = createSchemaNameAndPassword()
        return createSchema(schemaName, password, labels)
    }

    @Transactional
    open fun createSchema(schemaName: String, password: String, labels: Map<String, String?>): DatabaseSchema {

        if (!isCreateSchemaAllowed) {
            throw DatabaseServiceException("Schema creation has been disabled for this instance=$instanceName")
        }

        val schemaNameValid = databaseManager.createSchema(schemaName, password)
        val schemaData = databaseHotelDataDao.createSchemaData(schemaNameValid)
        databaseHotelDataDao.createUser(schemaData.id, UserType.SCHEMA.toString(), schemaData.name, password)

        val databaseSchema = findSchemaByName(schemaNameValid)
            ?: throw DatabaseServiceException("Expected schema [$schemaNameValid] to be created, but it was not")

        replaceLabels(databaseSchema, labels)

        integrations.forEach { integration -> integration.onSchemaCreated(databaseSchema) }

        return databaseSchema
    }

    @Transactional
    open fun deleteSchemaByCooldown(schemaName: String, optionalCooldownDuration: Duration?) {

        val cooldownDuration = optionalCooldownDuration ?: Duration.ofDays(cooldownDaysAfterDelete.toLong())

        val schema = findSchemaByName(schemaName) ?: throw DatabaseServiceException("No schema named [$schemaName]")

        schema.apply {
            logger.info(
                "Deactivating schema id={}, lastUsed={}, size(mb)={}, name={}, labels={}. Setting cooldown={}h",
                id, lastUsedDateString, sizeMb, name, labels, cooldownDuration.toHours()
            )
            databaseHotelDataDao.deactivateSchemaData(id, cooldownDuration)
            // We need to make sure that users can no longer connect to the schema. Let's just create a new random
            // password for the schema so that it is different from the one we have in the SchemaData.
            databaseManager.updatePassword(name, createSchemaNameAndPassword().second)
        }

        integrations.forEach { it.onSchemaDeleted(schema, cooldownDuration) }
    }

    @Transactional
    open fun permanentlyDeleteSchema(schemaName: String) {

        val schema = databaseHotelDataDao.findSchemaDataByNameIgnoreActive(schemaName)
            ?: throw DatabaseServiceException("No schema named [$schemaName]")

        logger.info("Permanently deleting schema {} (id={})", schemaName, schema.id)

        schema.apply {
            databaseHotelDataDao.deleteSchemaData(id)
            databaseManager.deleteSchema(name)
        }
    }

    @Transactional
    open fun deleteStaleSchemasByCooldown() {

        logger.info("Deleting stale schemas for server {} by cooldown", metaInfo.instanceName)

        val schemas = findAllStaleSchemas()
        logger.info("Found {} stale schemas", schemas.size)
        schemas.parallelStream().forEach {
            deleteSchemaByCooldown(it.name, Duration.ofDays(cooldownDaysForOldUnusedSchemas.toLong()))
        }
    }

    @Transactional
    open fun deleteSchemasWithExpiredCooldowns() {

        logger.info("Permanently deleting schemas with expired cooldowns for server {}", metaInfo.instanceName)

        val schemas = findAllSchemasWithExpiredCooldowns()
        schemas.map { it.name }.parallelStream().forEach(::permanentlyDeleteSchema)
    }

    fun findAllStaleSchemas(): Set<DatabaseSchema> {

        val daysAgo = Calendar.getInstance().run {
            add(Calendar.DAY_OF_MONTH, DAYS_BACK)
            time
        }

        fun DatabaseSchema.isSystemTestSchema() = this.labels["userId"]?.endsWith(":jenkins-builder") ?: false
        fun DatabaseSchema.isCandidateForDeletion() = this.isUnused || this.isSystemTestSchema()
        return findAllSchemas()
            .filter(DatabaseSchema::isCandidateForDeletion)
            .filter { s -> s.lastUsedOrCreatedDate.before(daysAgo) }
            .toSet()
    }

    fun findAllSchemasWithExpiredCooldowns(): Set<DatabaseSchema> {

        val schemaData = databaseHotelDataDao.findAllManagedSchemaDataByDeleteAfterDate(Date())
        return getDatabaseSchemaFromSchemaData(schemaData)
    }

    @Transactional
    open fun replaceLabels(schema: DatabaseSchema, labels: Map<String, String?>) {

        schema.labels = labels
        databaseHotelDataDao.replaceLabels(schema.id, schema.labels)
        integrations.forEach { it.onSchemaUpdated(schema) }
    }

    fun registerIntegration(integration: Integration) {

        this.integrations.add(integration)
    }

    private fun getDatabaseSchemaFromSchemaData(
        schemaData: List<SchemaData>,
        strategy: LookupDataFetchStrategy = LookupDataFetchStrategy.BY_ALL
    ): Set<DatabaseSchema> {

        val builder = when (strategy) {

            LookupDataFetchStrategy.BY_ALL -> DatabaseSchemaBuilder(
                metaInfo,
                jdbcUrlBuilder,
                databaseHotelDataDao.findAllUsers(),
                databaseManager.findAllNonSystemSchemas(),
                databaseHotelDataDao.findAllLabels(),
                resourceUsageCollector.schemaSizes
            )
            LookupDataFetchStrategy.FOR_EACH -> DatabaseSchemaBuilder(
                metaInfo,
                jdbcUrlBuilder,
                schemaData.flatMap { databaseHotelDataDao.findAllUsersForSchema(it.id) },
                schemaData.mapNotNull { databaseManager.findSchemaByName(it.name) },
                schemaData.flatMap { databaseHotelDataDao.findAllLabelsForSchema(it.id) },
                resourceUsageCollector.schemaSizes
            )
        }
        return builder.createMany(schemaData)
    }

    private fun getDatabaseSchemaFromSchemaData(schemaData: SchemaData): DatabaseSchema? {

        if (schemaData.schemaType != SchemaTypes.SCHEMA_TYPE_MANAGED) return null
        return getDatabaseSchemaFromSchemaData(listOf(schemaData), LookupDataFetchStrategy.FOR_EACH).firstOrNull()
    }

    enum class UserType {
        SCHEMA,
        READONLY,
        READWRITE
    }

    companion object {
        const val DAYS_BACK = -7
    }
}

private val DatabaseSchema.lastUsedDateString get() = this.lastUsedDate?.toInstant()?.toString() ?: "Never"
