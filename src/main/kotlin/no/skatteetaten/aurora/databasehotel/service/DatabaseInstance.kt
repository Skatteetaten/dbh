package no.skatteetaten.aurora.databasehotel.service

import mu.KotlinLogging
import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.SchemaTypes
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.internal.DatabaseSchemaBuilder
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.ArrayList
import java.util.Calendar
import java.util.Optional

private val logger = KotlinLogging.logger {}

open class DatabaseInstance(
    val metaInfo: DatabaseInstanceMetaInfo,
    private val databaseManager: DatabaseManager,
    val databaseHotelDataDao: DatabaseHotelDataDao,
    private val jdbcUrlBuilder: JdbcUrlBuilder,
    private val resourceUsageCollector: ResourceUsageCollector,
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

    fun findAllSchemas(labelsToMatch: Map<String, String?>? = null): Set<DatabaseSchema> {

        return if (labelsToMatch.isNullOrEmpty()) findAllSchemasUnfiltered()
        else findAllSchemasWithLabels(labelsToMatch)
    }

    private fun findAllSchemasUnfiltered(): Set<DatabaseSchema> {

        val schemaData = databaseHotelDataDao.findAllManagedSchemaData()
        val users = databaseHotelDataDao.findAllUsers()
        val schemas = databaseManager.findAllNonSystemSchemas()
        val labels = databaseHotelDataDao.findAllLabels()
        val schemaSizes = resourceUsageCollector.schemaSizes

        return DatabaseSchemaBuilder(metaInfo, jdbcUrlBuilder)
            .createMany(schemaData, schemas, users, labels, schemaSizes)
    }

    private fun findAllSchemasWithLabels(labelsToMatch: Map<String, String?>): Set<DatabaseSchema> {

        val schemaData = databaseHotelDataDao.findAllManagedSchemaDataByLabels(labelsToMatch)

        val users = schemaData.flatMap { databaseHotelDataDao.findAllUsersForSchema(it.id) }
        val schemas = schemaData.mapNotNull { databaseManager.findSchemaByName(it.name) }
        val labels = schemaData.flatMap { databaseHotelDataDao.findAllLabelsForSchema(it.id) }
        val schemaSizes = resourceUsageCollector.schemaSizes

        return DatabaseSchemaBuilder(metaInfo, jdbcUrlBuilder)
            .createMany(schemaData, schemas, users, labels, schemaSizes)
    }

    @Transactional
    open fun createSchema(labels: Map<String, String?>): DatabaseSchema {

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
    open fun deleteSchema(schemaName: String, optionalCooldownDuration: Duration?) {

        val cooldownDuration = optionalCooldownDuration ?: Duration.ofDays(cooldownDaysAfterDelete.toLong())

        val schema = findSchemaByName(schemaName) ?: throw DatabaseServiceException("No schema named [$schemaName]")

        schema.apply {
            logger.info(
                "Deleting schema id={}, lastUsed={}, size(mb)={}, name={}, labels={}. Setting cooldown={}h",
                id, lastUsedDateString, sizeMb, name, labels, cooldownDuration.toHours()
            )
            databaseHotelDataDao.deactivateSchemaData(id)
            databaseManager.deleteSchema(name)
            // We need to make sure that users can no longer connect to the schema. Let's just create a new random
            // password for the schema so that it is different from the one we have in the SchemaData.
            databaseManager.updatePassword(name, createSchemaNameAndPassword().second)
        }

        integrations.forEach { it.onSchemaDeleted(schema, cooldownDuration) }
    }

    @Transactional
    open fun pruneSchemasForDeletion() {

        logger.info("Deleting schemas old unused schemas for server {}", metaInfo.instanceName)

        val schemas = findAllSchemasForDeletion()
        logger.info("Found {} old and unused schemas", schemas.size)
        schemas.parallelStream().forEach {
            deleteSchema(it.name, Duration.ofDays(cooldownDaysForOldUnusedSchemas.toLong()))
        }
    }

    fun findAllSchemasForDeletion(): Set<DatabaseSchema> {

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

    @Transactional
    open fun replaceLabels(schema: DatabaseSchema, labels: Map<String, String?>) {

        schema.labels = labels
        databaseHotelDataDao.replaceLabels(schema.id, schema.labels)
        integrations.forEach { it.onSchemaUpdated(schema) }
    }

    fun registerIntegration(integration: Integration) {

        this.integrations.add(integration)
    }

    private fun getDatabaseSchemaFromSchemaData(schemaData: SchemaData): DatabaseSchema? {

        if (schemaData.schemaType != SchemaTypes.SCHEMA_TYPE_MANAGED) return null
        val schema = databaseManager.findSchemaByName(schemaData.name) ?: return null

        val users = databaseHotelDataDao.findAllUsersForSchema(schemaData.id)
        val labels = databaseHotelDataDao.findAllLabelsForSchema(schemaData.id)

        val schemaSize = resourceUsageCollector.getSchemaSize(schemaData.name)

        return DatabaseSchemaBuilder(metaInfo, jdbcUrlBuilder)
            .createOne(schemaData, schema, users, Optional.ofNullable(labels), schemaSize)
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
