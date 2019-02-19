package no.skatteetaten.aurora.databasehotel.service

import com.google.common.collect.Lists
import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.internal.SchemaLabelMatcher.findAllMatchingSchemas
import org.apache.commons.lang3.tuple.Pair
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.lang.String.format
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Duration
import java.util.Optional

data class DatabaseInstanceRequirements(
    val databaseEngine: DatabaseEngine = DatabaseEngine.ORACLE,
    val instanceName: String? = null,
    val instanceLabels: Map<String, String> = emptyMap(),
    val fallback: Boolean = true
)

@Service
class DatabaseHotelService(private val databaseHotelAdminService: DatabaseHotelAdminService) {

    fun findSchemaById(id: String): Optional<Pair<DatabaseSchema, DatabaseInstance?>> {

        val candidates = Lists.newArrayList<Pair<DatabaseSchema, DatabaseInstance?>>()

        val allDatabaseInstances = databaseHotelAdminService.findAllDatabaseInstances()
        for (databaseInstance in allDatabaseInstances) {
            val schemaAndInstance = databaseInstance.findSchemaById(id)
                    .map { dbs -> Pair.of(dbs, databaseInstance) }
            schemaAndInstance.ifPresent { candidates.add(it) }
        }

        val schema = databaseHotelAdminService.externalSchemaManager?.findSchemaById(id)?.orElse(null)
        schema
                ?.let { Pair.of(it, null as DatabaseInstance?) }
                ?.let { candidates.add(it) }

        verifyOnlyOneCandidate(id, candidates)
        return if (candidates.size == 0) Optional.empty() else Optional.of(candidates[0])
    }

    fun findAllDatabaseSchemas(engine: DatabaseEngine?): Set<DatabaseSchema> {

        return findAllDatabaseSchemasByLabels(engine, emptyMap())
    }

    fun findAllDatabaseSchemasByLabels(engine: DatabaseEngine? = null, labelsToMatch: Map<String, String?> = emptyMap()): Set<DatabaseSchema> {

        val schemas = databaseHotelAdminService.findAllDatabaseInstances(engine)
                .flatMap { it.findAllSchemas(labelsToMatch) }.toSet()
        val externalSchemas = databaseHotelAdminService.externalSchemaManager?.findAllSchemas()
        val matchingExternalSchemas = findAllMatchingSchemas(externalSchemas, labelsToMatch)
        return schemas + matchingExternalSchemas
    }

    fun findAllDatabaseSchemasForDeletion(): Set<DatabaseSchema> =
            databaseHotelAdminService.findAllDatabaseInstances()
                    .flatMap { it.findAllSchemasForDeletion() }.toSet()

    fun createSchema(requirements: DatabaseInstanceRequirements = DatabaseInstanceRequirements()): DatabaseSchema =
            createSchema(requirements, null)

    fun createSchema(requirements: DatabaseInstanceRequirements, labels: Map<String, String>?): DatabaseSchema {

        val databaseInstance = databaseHotelAdminService.findDatabaseInstanceOrFail(requirements)
        val schema = databaseInstance.createSchema(labels)

        log.info("Created schema name={}, id={} with labels={}", schema.name, schema.id, schema.labels.toString())
        return schema
    }

    fun deleteSchemaById(id: String, cooldownDuration: Duration? = null) {

        findSchemaById(id).ifPresent { schemaAndInstance ->
            val (_, _, _, name) = schemaAndInstance.left
            val databaseInstance = schemaAndInstance.right

            when (databaseInstance) {
                null -> databaseHotelAdminService.externalSchemaManager?.deleteSchema(id)
                else -> databaseInstance.deleteSchema(name, cooldownDuration)
            }
            if (databaseInstance != null) {
            } else {
                // If the schema was not found on a database instance, it is an external schema
            }
        }
    }

    fun validateConnection(id: String) =
        findSchemaById(id).orElse(null)?.let {
            val user = it.left.users.first()
            validateConnection(it.left.jdbcUrl, user.name, user.password)
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
        labels: Map<String, String>,
        username: String? = null,
        jdbcUrl: String? = null,
        password: String? = null
    ): DatabaseSchema {

        log.info("Updating labels for schema with id={} to labels={}", id, labels)

        val schemaAndInstance = findSchemaById(id).orElseThrow { DatabaseServiceException(format("No such schema %s", id)) }

        val databaseInstance = schemaAndInstance.right
        return if (databaseInstance != null) {
            val schema = schemaAndInstance.left
            databaseInstance.replaceLabels(schema, labels)
            schema
        } else {
            val schema = schemaAndInstance.left
            databaseHotelAdminService.externalSchemaManager?.let { externalSchemaManager ->
                externalSchemaManager.replaceLabels(schema, labels)
                externalSchemaManager.updateConnectionInfo(schema.id, username, jdbcUrl, password)
                externalSchemaManager.findSchemaById(id).orElse(null)
            }!!
        }
    }

    fun registerExternalSchema(username: String, password: String, jdbcUrl: String, labels: Map<String, String>): DatabaseSchema {
        val externalSchemaManager = databaseHotelAdminService.externalSchemaManager
                ?: throw DatabaseServiceException("External Schema Manager has not been registered")
        return externalSchemaManager.registerSchema(username, password, jdbcUrl, labels)
    }

    companion object {

        private val log = LoggerFactory.getLogger(DatabaseHotelService::class.java)

        private fun verifyOnlyOneCandidate(id: String, candidates: List<Pair<DatabaseSchema, DatabaseInstance?>>) {
            if (candidates.size <= 1) {
                return
            }

            candidates.stream()
                    .map { candidate ->
                        val (_, _, jdbcUrl, name) = candidate.left
                        val instance = candidate.right
                        val host = instance?.metaInfo?.host
                        format("[schemaName=%s, jdbcUrl=%s, hostName=%s]", name, jdbcUrl, host)
                    }
                    .reduce { s, s2 -> format("%s, %s", s, s2) }
                    .ifPresent { candidatesString ->
                        val error = format("More than one schema from different database servers matched the specified id [%s]: %s",
                                id, candidatesString)
                        throw IllegalStateException(error)
                    }
        }
    }
}
