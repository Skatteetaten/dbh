package no.skatteetaten.aurora.databasehotel.web.rest

import io.micrometer.core.annotation.Timed
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.time.Duration
import java.util.Date
import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import no.skatteetaten.aurora.databasehotel.service.DatabaseInstanceRequirements
import no.skatteetaten.aurora.databasehotel.toDatabaseEngine
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class SchemaMetadataResource(val sizeInMb: Double?)

data class DatabaseSchemaResource(
    val id: String,
    val type: String,
    val jdbcUrl: String,
    val name: String,
    val createdDate: Date,
    val lastUsedDate: Date?,
    val databaseInstance: DatabaseInstanceResource,
    val users: List<UserResource>,
    val labels: Map<String, String?>,
    val metadata: SchemaMetadataResource
)

data class SchemaCreationRequest(
    val engine: DatabaseEngine = DatabaseEngine.ORACLE,
    val instanceName: String? = null,
    val instanceLabels: Map<String, String> = emptyMap(),
    val instanceFallback: Boolean? = null,
    val labels: Map<String, String?>? = null,
    val schema: Schema? = null
) {
    val fallback: Boolean
        get() = instanceFallback ?: (engine == DatabaseEngine.ORACLE)
}

data class JdbcUser(
    val jdbcUrl: String,
    val username: String,
    val password: String
)

data class ConnectionVerificationRequest(
    val id: String? = null,
    val jdbcUser: JdbcUser? = null
)

data class Schema(val username: String, val password: String, val jdbcUrl: String) {
    val isValid = username.isNotEmpty() && password.isNotEmpty() && jdbcUrl.isNotEmpty()
}

@RestController
@RequestMapping("/api/v1/schema")
class DatabaseSchemaController(
    private val databaseHotelService: DatabaseHotelService,
    @Value("\${database-config.schemaListingAllowed}") private val schemaListingAllowed: Boolean,
    @Value("\${database-config.dropAllowed}") private val dropAllowed: Boolean
) {

    @GetMapping("/{id}")
    @Timed
    fun findById(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {

        val databaseSchema = databaseHotelService.findSchemaById(id)?.first
            ?: throw IllegalArgumentException("No such schema $id")
        return Responses.okResponse(databaseSchema.toResource())
    }

    @DeleteMapping("/{id}")
    @Timed
    fun deleteById(
        @PathVariable id: String,
        @RequestHeader(name = "cooldown-duration-seconds", required = false) cooldownDurationSeconds: Long?
    ): ResponseEntity<ApiResponse<*>> {

        if (!dropAllowed) {
            throw OperationDisabledException("Schema deletion has been disabled for this instance")
        }

        val cooldownDuration = cooldownDurationSeconds?.let { Duration.ofSeconds(it) }
        databaseHotelService.deleteSchemaByCooldown(id, cooldownDuration)
        return Responses.okResponse()
    }

    @GetMapping("/")
    @Timed
    fun findAll(
        @RequestParam(name = "engine", required = false) engineName: String?,
        @RequestParam(required = false, defaultValue = "") labels: String,
        @RequestParam(required = false) q: String?
    ): ResponseEntity<ApiResponse<*>> {

        if (!schemaListingAllowed) {
            throw OperationDisabledException("Schema listing has been disabled for this instance")
        }
        val engine = engineName?.toDatabaseEngine()
        val schemas: Set<DatabaseSchema> = when {
            q == "stale" -> databaseHotelService.findAllStaleDatabaseSchemas()
            labels.isBlank() -> databaseHotelService.findAllDatabaseSchemas(engine)
            else -> databaseHotelService.findAllDatabaseSchemasByLabels(engine, parseLabelsParam(labels))
        }

        val resources = schemas
            .sortedBy { it.lastUsedOrCreatedDate }
            .map { it.toResource() }
        return Responses.okResponse(resources)
    }

    @PutMapping("/{id}")
    @Timed
    fun update(
        @PathVariable id: String,
        @RequestBody schemaCreationRequest: SchemaCreationRequest
    ): ResponseEntity<ApiResponse<*>> {

        val labels = schemaCreationRequest.labels ?: emptyMap()
        val schema = schemaCreationRequest.schema
        val databaseSchema = when {
            schema == null -> databaseHotelService.updateSchema(id, labels)
            schema.isValid -> databaseHotelService.updateSchema(
                id,
                labels,
                schema.username,
                schema.jdbcUrl,
                schema.password
            )
            else -> throw IllegalArgumentException("Missing JDBC input")
        }
        return Responses.okResponse(databaseSchema.toResource())
    }

    @PostMapping("/")
    @Timed
    fun create(@RequestBody schemaCreationRequest: SchemaCreationRequest): ResponseEntity<ApiResponse<*>> {

        val labels = schemaCreationRequest.labels ?: emptyMap()
        val schema = schemaCreationRequest.schema
        val databaseSchema = when {
            schema == null -> {
                val instanceRequirements = DatabaseInstanceRequirements(
                    databaseEngine = schemaCreationRequest.engine,
                    instanceName = schemaCreationRequest.instanceName,
                    instanceLabels = schemaCreationRequest.instanceLabels,
                    instanceFallback = schemaCreationRequest.fallback
                )
                databaseHotelService.createSchema(instanceRequirements, labels)
            }
            schema.isValid -> databaseHotelService.registerExternalSchema(
                schema.username,
                schema.password,
                schema.jdbcUrl,
                labels
            )
            else -> throw IllegalArgumentException("Missing JDBC input")
        }
        return Responses.okResponse(databaseSchema.toResource())
    }

    @PutMapping("/validate")
    fun validate(@RequestBody connectionVerificationRequest: ConnectionVerificationRequest): ResponseEntity<ApiResponse<*>> {
        val success = connectionVerificationRequest.id?.let {
            databaseHotelService.validateConnection(it)
        } ?: connectionVerificationRequest.jdbcUser?.let {
            databaseHotelService.validateConnection(it.jdbcUrl, it.username, it.password)
        } ?: throw IllegalArgumentException("id or jdbcUser is required")
        return Responses.okResponse(success)
    }
}

fun DatabaseSchema.toResource() = DatabaseSchemaResource(
    id = id,
    type = type.toString(),
    jdbcUrl = jdbcUrl,
    name = name,
    createdDate = createdDate,
    lastUsedDate = lastUsedDate,
    databaseInstance = databaseInstanceMetaInfo.toResource(),
    users = users.map { it.toResource() },
    labels = labels,
    metadata = SchemaMetadataResource(metadata?.sizeInMb)
)

fun parseLabelsParam(labelsParam: String): Map<String, String?> {

    val labelsDecoded: String = try {
        URLDecoder.decode(labelsParam, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        throw IllegalArgumentException(e)
    }

    if (labelsDecoded.isBlank()) return emptyMap()

    val labelsUnparsed = labelsDecoded.splitRemoveEmpties(",")

    return labelsUnparsed
        .map { it.splitRemoveEmpties("=") }
        .filter { it.firstOrNull()?.emptyToNull() != null }
        .map {
            val name = it.first()
            val value = it.takeIf { it.size > 1 }?.get(1)
            Pair(name, value)
        }.toMap()
}

private fun String.splitRemoveEmpties(delimiter: String) =
    split(delimiter)
        .dropLastWhile { it.isEmpty() }
        .map { it.trim() }

private fun String.emptyToNull(): String? = if (isEmpty()) null else this
