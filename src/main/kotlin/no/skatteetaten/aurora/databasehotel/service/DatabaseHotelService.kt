package no.skatteetaten.aurora.databasehotel.service

import mu.KotlinLogging
import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.internal.SchemaLabelMatcher.findAllMatchingSchemas
import org.springframework.stereotype.Service
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Duration

private val logger = KotlinLogging.logger {}

data class DatabaseInstanceRequirements(
    val databaseEngine: DatabaseEngine = DatabaseEngine.ORACLE,
    val instanceName: String? = null,
    val instanceLabels: Map<String, String> = emptyMap(),
    val instanceFallback: Boolean = true
)

@Service
class DatabaseHotelService(private val databaseHotelAdminService: DatabaseHotelAdminService) {

    fun findSchemaById(id: String): Pair<DatabaseSchema, DatabaseInstance?>? {

        val candidates = mutableListOf<Pair<DatabaseSchema, DatabaseInstance?>>()

        val allDatabaseInstances = databaseHotelAdminService.findAllDatabaseInstances()
        for (databaseInstance in allDatabaseInstances) {
            val schemaAndInstance = databaseInstance.findSchemaById(id)
                ?.let { dbs -> Pair(dbs, databaseInstance) }
            schemaAndInstance?.let { candidates.add(it) }
        }

        databaseHotelAdminService.externalSchemaManager?.findSchemaById(id)
            ?.let { Pair(it, null) }
            ?.let { candidates.add(it) }

        verifyOnlyOneCandidate(id, candidates)
        return candidates.firstOrNull()
    }

    fun findAllDatabaseSchemas(engine: DatabaseEngine?): Set<DatabaseSchema> {

        return findAllDatabaseSchemasByLabels(engine, emptyMap())
    }

    fun findAllDatabaseSchemasByLabels(
        engine: DatabaseEngine? = null,
        labelsToMatch: Map<String, String?> = emptyMap()
    ): Set<DatabaseSchema> {

        val schemas = databaseHotelAdminService.findAllDatabaseInstances(engine)
            .flatMap { it.findAllSchemas(labelsToMatch) }.toSet()
        val externalSchemas = databaseHotelAdminService.externalSchemaManager?.findAllSchemas() ?: emptySet()
        val matchingExternalSchemas = findAllMatchingSchemas(externalSchemas, labelsToMatch)
        return schemas + matchingExternalSchemas
    }

    fun findAllDatabaseSchemasForDeletion(): Set<DatabaseSchema> =
        databaseHotelAdminService.findAllDatabaseInstances()
            .flatMap { it.findAllSchemasForDeletion() }.toSet()

    fun createSchema(requirements: DatabaseInstanceRequirements = DatabaseInstanceRequirements()): DatabaseSchema =
        createSchema(requirements)

    fun createSchema(
        requirements: DatabaseInstanceRequirements,
        labels: Map<String, String?> = emptyMap()
    ): DatabaseSchema {

        val databaseInstance = databaseHotelAdminService.findDatabaseInstanceOrFail(requirements)
        val schema = databaseInstance.createSchema(labels)

        logger.info("Created schema name={}, id={} with labels={}", schema.name, schema.id, schema.labels.toString())
        return schema
    }

    fun deleteSchemaById(id: String, cooldownDuration: Duration? = null) {

        findSchemaById(id)?.let { (schema, databaseInstance) ->

            when (databaseInstance) {
                null -> databaseHotelAdminService.externalSchemaManager?.deleteSchema(id)
                else -> databaseInstance.deleteSchema(schema.name, cooldownDuration)
            }
        }
    }

    fun validateConnection(id: String) =
        findSchemaById(id)?.let { (schema, _) ->
            val user = schema.users.first()
            validateConnection(schema.jdbcUrl, user.name, user.password)
        } ?: throw IllegalArgumentException("no database schema found for id: $id")

    fun validateConnection(jdbcUrl: String, username: String, password: String) =
        try {
            DriverManager.getConnection(jdbcUrl, username, password).use { true }
        } catch (ex: SQLException) {
            false
        }

    @JvmOverloads
    fun updateSchema(
        id: String,
        labels: Map<String, String?>,
        username: String? = null,
        jdbcUrl: String? = null,
        password: String? = null
    ): DatabaseSchema? {

        logger.info("Updating labels for schema with id={} to labels={}", id, labels)

        val (schema, databaseInstance) = findSchemaById(id)
            ?: throw DatabaseServiceException("No such schema $id")

        return if (databaseInstance != null) {
            databaseInstance.replaceLabels(schema, labels)
            schema
        } else {
            val externalSchemaManager = databaseHotelAdminService.externalSchemaManager
                ?: throw IllegalStateException("Unable to update schema $id - no ExternalSchemaManager registered")
            externalSchemaManager.run {
                replaceLabels(schema, labels)
                updateConnectionInfo(schema.id, username, jdbcUrl, password)
                findSchemaById(id)
            }
        }
    }

    fun registerExternalSchema(
        username: String,
        password: String,
        jdbcUrl: String,
        labels: Map<String, String?>
    ): DatabaseSchema {
        val externalSchemaManager = databaseHotelAdminService.externalSchemaManager
            ?: throw DatabaseServiceException("External Schema Manager has not been registered")
        return externalSchemaManager.registerSchema(username, password, jdbcUrl, labels)
    }

    companion object {

        private fun verifyOnlyOneCandidate(
            id: String,
            candidates: List<Pair<DatabaseSchema, DatabaseInstance?>>
        ) {
            if (candidates.size <= 1) return

            candidates.joinToString(", ") { (schema, instance) ->
                val host = instance?.metaInfo?.host
                "[schemaName=${schema.name}, jdbcUrl=${schema.jdbcUrl}, hostName=$host]"
            }
                .takeIf { it.isNotEmpty() }
                ?.run { throw IllegalStateException("More than one schema from different database servers matched the specified id [$id]: $this") }
        }
    }
}
