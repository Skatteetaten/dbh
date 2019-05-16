package no.skatteetaten.aurora.databasehotel.service

import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.SchemaTypes
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.internal.DatabaseSchemaBuilder
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.ArrayList
import java.util.Calendar
import java.util.Optional

open class DatabaseInstance(
    val metaInfo: DatabaseInstanceMetaInfo,
    private val databaseManager: DatabaseManager,
    val databaseHotelDataDao: DatabaseHotelDataDao,
    private val jdbcUrlBuilder: JdbcUrlBuilder, private val resourceUsageCollector: ResourceUsageCollector,
    private val cooldownDaysAfterDelete: Int, private val cooldownDaysForOldUnusedSchemas: Int
) {

    private val integrations = ArrayList<Integration>()

    val isCreateSchemaAllowed: Boolean get() = metaInfo.createSchemaAllowed

    val instanceName: String get() = metaInfo.instanceName

    fun findSchemaById(id: String): DatabaseSchema? =
        databaseHotelDataDao.findSchemaDataById(id)?.let(this::getDatabaseSchemaFromSchemaData)

    fun findSchemaByName(name: String): Optional<DatabaseSchema> {

        return databaseHotelDataDao.findSchemaDataByName(name).map(this::getDatabaseSchemaFromSchemaData)
    }

    fun findAllSchemas(labelsToMatch: Map<String, String?>?): Set<DatabaseSchema> {

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
    open fun createSchema(labelsOption: Map<String, String>): DatabaseSchema {

        val schemaNameAndPassword = createSchemaNameAndPassword()
        val schemaName = schemaNameAndPassword.left
        val password = schemaNameAndPassword.right

        return createSchema(schemaName, password, labelsOption)
    }

    @Transactional
    open fun createSchema(schemaName: String, labelsOption: Map<String, String>): DatabaseSchema {

        val schemaNameAndPassword = createSchemaNameAndPassword()
        val password = schemaNameAndPassword.right

        return createSchema(schemaName, password, labelsOption)
    }

    @Transactional
    open fun createSchema(schemaName: String, password: String, labelsOption: Map<String, String>): DatabaseSchema {

        if (!isCreateSchemaAllowed) {
            throw DatabaseServiceException(
                String.format(
                    "Schema creation has been disabled for this instance=%s",
                    instanceName
                )
            )
        }

        val schemaNameValid = databaseManager.createSchema(schemaName, password)
        val (id, name) = databaseHotelDataDao.createSchemaData(schemaNameValid)
        databaseHotelDataDao.createUser(id, UserType.SCHEMA.toString(), name, password)

        val databaseSchema = findSchemaByName(schemaNameValid).orElseThrow {
            DatabaseServiceException(
                String.format("Expected schema [%s] to be created, but it was not", schemaNameValid)
            )
        }

        Optional.ofNullable(labelsOption).ifPresent { labels -> replaceLabels(databaseSchema, labels) }

        integrations.forEach { integration -> integration.onSchemaCreated(databaseSchema) }

        return databaseSchema
    }

    @Transactional
    open fun deleteSchema(schemaName: String, deleteParams: DeleteParams) {

        val optionalSchema = findSchemaByName(schemaName)
        if (deleteParams.isAssertExists) {
            optionalSchema
                .orElseThrow { DatabaseServiceException(String.format("No schema named [%s]", schemaName)) }
        }

        optionalSchema.ifPresent { databaseSchema ->
            databaseHotelDataDao.findSchemaDataByName(schemaName).ifPresent { (id, name) ->
                val lastUsedDate = databaseSchema.lastUsedDate
                val lastUsedString = lastUsedDate?.toInstant()?.toString() ?: "Never"
                LOGGER.info(
                    "Deleting schema id={}, lastUsed={}, size(mb)={}, name={}, labels={}. Setting cooldown={}h",
                    id, lastUsedString, databaseSchema.sizeMb, name,
                    databaseSchema.labels, deleteParams.cooldownDuration.toHours()
                )
                databaseHotelDataDao.deactivateSchemaData(id)
                // We need to make sure that users can no longer connect to the schema. Let's just create a new random
                // password for the schema so that it is different from the one we have in the SchemaData.
                databaseManager.updatePassword(schemaName, createSchemaNameAndPassword().right)
            }
            integrations.forEach { integration ->
                integration.onSchemaDeleted(
                    databaseSchema,
                    deleteParams.cooldownDuration
                )
            }
        }
    }

    @Transactional
    open fun deleteSchema(schemaName: String, cooldownDuration: Duration?) {
        var cooldownDuration = cooldownDuration

        if (cooldownDuration == null) {
            cooldownDuration = Duration.ofDays(cooldownDaysAfterDelete.toLong())
        }

        deleteSchema(schemaName, DeleteParams(cooldownDuration))
    }

    @Transactional
    open fun deleteUnusedSchemas() {

        LOGGER.info("Deleting schemas old unused schemas for server {}", metaInfo.instanceName)

        val schemas = findAllSchemasForDeletion()
        LOGGER.info("Found {} old and unused schemas", schemas.size)
        schemas.parallelStream().forEach { (_, _, _, name) ->
            deleteSchema(
                name,
                Duration.ofDays(cooldownDaysForOldUnusedSchemas.toLong())
            )
        }
    }

    fun findAllSchemasForDeletion(): Set<DatabaseSchema> {

        val daysAgo = Calendar.getInstance().let {
            it.add(Calendar.DAY_OF_MONTH, DAYS_BACK)
            it.time
        }

        fun DatabaseSchema.isSystemTestSchema() = this.labels["userId"]?.endsWith(":jenkins-builder") ?: false
        return findAllSchemas(null)
            .filter { it.isUnused || it.isSystemTestSchema() }
            .filter { s -> s.lastUsedOrCreatedDate.before(daysAgo) }
            .toSet()
    }

    @Transactional
    open fun replaceLabels(schema: DatabaseSchema, labels: Map<String, String>) {

        schema.labels = labels
        databaseHotelDataDao.replaceLabels(schema.id, schema.labels)
        integrations.forEach { integration -> integration.onSchemaUpdated(schema) }
    }

    fun registerIntegration(integration: Integration) {

        this.integrations.add(integration)
    }

    private fun getDatabaseSchemaFromSchemaData(schemaData: SchemaData): DatabaseSchema? {

        if (schemaData.schemaType != SchemaTypes.SCHEMA_TYPE_MANAGED) return null

        return databaseManager.findSchemaByName(schemaData.name).let { schema ->
            val users = databaseHotelDataDao.findAllUsersForSchema(schemaData.id)
            val labels = databaseHotelDataDao.findAllLabelsForSchema(schemaData.id)

            val schemaSize = resourceUsageCollector.getSchemaSize(schemaData.name)

            DatabaseSchemaBuilder(metaInfo, jdbcUrlBuilder).createOne(
                schemaData, schema, users,
                Optional.ofNullable(labels), schemaSize
            )
        }
    }

    enum class UserType {
        SCHEMA,
        READONLY,
        READWRITE
    }

    companion object {

        val DAYS_BACK = -7

        private val LOGGER = LoggerFactory.getLogger(DatabaseInstance::class.java)
    }
}
